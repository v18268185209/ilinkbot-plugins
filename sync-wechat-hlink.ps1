[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$SourceRoot = 'F:\codes\news\codes\plugins\wechat-hlink-plugins',
    [string]$TargetRoot = '',
    [string[]]$Exclude = @(),
    [string]$ExcludeFile = '',
    [switch]$NoDefaultExclude,
    [switch]$SkipExcludeFile,
    [switch]$CleanExcludedInTarget
)

<#
.SYNOPSIS
Sync sources, src and pom.xml from wechat-hlink-plugins into this repo.

.DESCRIPTION
This script uses overwrite-copy behavior:
- Copy files from source to target and overwrite same-name files
- Keep extra files that already exist in target
- Exclude files or directories by name or by source-relative path
- Default excludes: node_modules, target, *.log
- Auto-load exclude rules from sync.exclude.txt next to this script

.EXAMPLE
.\sync-wechat-hlink.ps1

.EXAMPLE
.\sync-wechat-hlink.ps1 -Exclude node_modules,target,*.log

.EXAMPLE
.\sync-wechat-hlink.ps1 -Exclude "node_modules,target,*.log","sources/dist","src/main/resources/*.yaml"

.EXAMPLE
.\sync-wechat-hlink.ps1 -Exclude node_modules,target,*.log -WhatIf

.EXAMPLE
.\sync-wechat-hlink.ps1 -CleanExcludedInTarget

.EXAMPLE
.\sync-wechat-hlink.ps1 -ExcludeFile .\sync.exclude.txt
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Stats = @{
    CopiedFiles = 0
    CreatedDirs = 0
    Skipped     = 0
}

if (-not [string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    $scriptDirectory = $PSScriptRoot
}
else {
    $scriptDirectory = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
}

if ([string]::IsNullOrWhiteSpace($TargetRoot)) {
    $TargetRoot = $scriptDirectory
}

$isExplicitExcludeFile = -not [string]::IsNullOrWhiteSpace($ExcludeFile)
if ([string]::IsNullOrWhiteSpace($ExcludeFile)) {
    $ExcludeFile = Join-Path -Path $scriptDirectory -ChildPath 'sync.exclude.txt'
}

function Resolve-FullPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return [System.IO.Path]::GetFullPath($Path)
}

function Normalize-ExcludePatterns {
    param(
        [string[]]$Patterns
    )

    $normalized = New-Object System.Collections.Generic.List[string]

    foreach ($pattern in $Patterns) {
        if ([string]::IsNullOrWhiteSpace($pattern)) {
            continue
        }

        foreach ($candidate in ($pattern -split ',')) {
            $trimmed = $candidate.Trim()
            if ([string]::IsNullOrWhiteSpace($trimmed)) {
                continue
            }

            $normalized.Add(($trimmed -replace '\\', '/'))
        }
    }

    return $normalized.ToArray()
}

function Get-DeduplicatedPatterns {
    param(
        [string[]]$Patterns
    )

    $seen = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
    $result = New-Object System.Collections.Generic.List[string]

    foreach ($pattern in $Patterns) {
        if ([string]::IsNullOrWhiteSpace($pattern)) {
            continue
        }

        if ($seen.Add($pattern)) {
            $result.Add($pattern)
        }
    }

    return $result.ToArray()
}

function Read-ExcludePatternsFromFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $patterns = New-Object System.Collections.Generic.List[string]

    foreach ($line in (Get-Content -LiteralPath $Path)) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            continue
        }

        if ($trimmed.StartsWith('#') -or $trimmed.StartsWith(';')) {
            continue
        }

        $patterns.Add($trimmed)
    }

    return $patterns.ToArray()
}

$defaultExcludePatterns = @('node_modules', 'target', '*.log')
$resolvedSourceRoot = Resolve-FullPath -Path $SourceRoot
$resolvedTargetRoot = Resolve-FullPath -Path $TargetRoot
[string[]]$effectiveExcludeInput = @()
if (-not $NoDefaultExclude) {
    $effectiveExcludeInput += $defaultExcludePatterns
}
$resolvedExcludeFile = ''
if (-not $SkipExcludeFile) {
    $resolvedExcludeFile = Resolve-FullPath -Path $ExcludeFile

    if (Test-Path -LiteralPath $resolvedExcludeFile -PathType Leaf) {
        $effectiveExcludeInput += @(Read-ExcludePatternsFromFile -Path $resolvedExcludeFile)
    }
    elseif ($isExplicitExcludeFile) {
        throw "Exclude file does not exist: $resolvedExcludeFile"
    }
}
$effectiveExcludeInput += $Exclude
[string[]]$excludePatterns = @(Get-DeduplicatedPatterns -Patterns (Normalize-ExcludePatterns -Patterns $effectiveExcludeInput))

if (-not (Test-Path -LiteralPath $resolvedSourceRoot -PathType Container)) {
    throw "Source project directory does not exist: $resolvedSourceRoot"
}

if ($resolvedSourceRoot -eq $resolvedTargetRoot) {
    throw 'Source root and target root cannot be the same.'
}

function Test-PathWithinRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,

        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $resolvedRoot = Resolve-FullPath -Path $Root
    $resolvedPath = Resolve-FullPath -Path $Path
    $rootPrefix = $resolvedRoot.TrimEnd('\', '/') + '\'

    return $resolvedPath.Equals($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
        $resolvedPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)
}

function Get-RelativePathFromRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,

        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $resolvedRoot = Resolve-FullPath -Path $Root
    $fullPath = Resolve-FullPath -Path $Path
    $rootPrefix = $resolvedRoot.TrimEnd('\', '/') + '\'

    if ($fullPath -eq $resolvedRoot) {
        return ''
    }

    if (-not $fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside root: $fullPath"
    }

    return ($fullPath.Substring($rootPrefix.Length) -replace '\\', '/')
}

function Get-ProjectRelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return Get-RelativePathFromRoot -Root $resolvedSourceRoot -Path $Path
}

function Test-IsExcludedByRelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativePath,

        [Parameter(Mandatory = $true)]
        [string]$LeafName
    )

    if ($excludePatterns.Count -eq 0) {
        return $false
    }

    foreach ($pattern in $excludePatterns) {
        if ($LeafName -like $pattern) {
            return $true
        }

        if ($RelativePath -like $pattern) {
            return $true
        }

        if ($RelativePath -like "*/$pattern") {
            return $true
        }

        if ($RelativePath -like "$pattern/*") {
            return $true
        }
    }

    return $false
}

function Test-IsExcluded {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $relativePath = Get-ProjectRelativePath -Path $Path
    $leafName = Split-Path -Path $Path -Leaf
    return (Test-IsExcludedByRelativePath -RelativePath $relativePath -LeafName $leafName)
}

function Ensure-Directory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (Test-Path -LiteralPath $Path -PathType Container) {
        return
    }

    if ($PSCmdlet.ShouldProcess($Path, 'Create directory')) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
        $script:Stats.CreatedDirs++
        Write-Verbose "Created directory: $Path"
    }
}

function Copy-SourceFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourcePath,

        [Parameter(Mandatory = $true)]
        [string]$DestinationPath
    )

    if (Test-IsExcluded -Path $SourcePath) {
        $script:Stats.Skipped++
        Write-Host "[skip] $(Get-ProjectRelativePath -Path $SourcePath)"
        return
    }

    $destinationParent = Split-Path -Path $DestinationPath -Parent
    if (-not [string]::IsNullOrWhiteSpace($destinationParent)) {
        Ensure-Directory -Path $destinationParent
    }

    $relativePath = Get-ProjectRelativePath -Path $SourcePath
    if ($PSCmdlet.ShouldProcess($DestinationPath, "Copy file from $relativePath")) {
        Copy-Item -LiteralPath $SourcePath -Destination $DestinationPath -Force
        $script:Stats.CopiedFiles++
        Write-Verbose "Copied file: $relativePath"
    }
}

function Copy-SourceDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourcePath,

        [Parameter(Mandatory = $true)]
        [string]$DestinationPath
    )

    if (Test-IsExcluded -Path $SourcePath) {
        $script:Stats.Skipped++
        Write-Host "[skip] $(Get-ProjectRelativePath -Path $SourcePath)"
        return
    }

    Ensure-Directory -Path $DestinationPath

    foreach ($item in (Get-ChildItem -LiteralPath $SourcePath -Force)) {
        $targetPath = Join-Path -Path $DestinationPath -ChildPath $item.Name

        if ($item.PSIsContainer) {
            Copy-SourceDirectory -SourcePath $item.FullName -DestinationPath $targetPath
            continue
        }

        Copy-SourceFile -SourcePath $item.FullName -DestinationPath $targetPath
    }
}

function Remove-TargetPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    if (-not (Test-PathWithinRoot -Root $resolvedTargetRoot -Path $Path)) {
        throw "Refusing to remove path outside target root: $Path"
    }

    $item = Get-Item -LiteralPath $Path -Force
    $targetRelativePath = Get-RelativePathFromRoot -Root $resolvedTargetRoot -Path $item.FullName

    if ($PSCmdlet.ShouldProcess($item.FullName, "Remove excluded target path $targetRelativePath")) {
        if ($item.PSIsContainer) {
            Remove-Item -LiteralPath $item.FullName -Recurse -Force
        }
        else {
            Remove-Item -LiteralPath $item.FullName -Force
        }
        Write-Host "[clean] $targetRelativePath"
    }
}

function Remove-ExcludedTargetItems {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $item = Get-Item -LiteralPath $Path -Force
    $rootRelativePath = Get-RelativePathFromRoot -Root $resolvedTargetRoot -Path $item.FullName
    if (Test-IsExcludedByRelativePath -RelativePath $rootRelativePath -LeafName $item.Name) {
        Remove-TargetPath -Path $item.FullName
        return
    }

    if (-not $item.PSIsContainer) {
        return
    }

    $children = Get-ChildItem -LiteralPath $item.FullName -Force -Recurse |
        Sort-Object -Property FullName -Descending

    foreach ($child in $children) {
        $childRelativePath = Get-RelativePathFromRoot -Root $resolvedTargetRoot -Path $child.FullName
        if (Test-IsExcludedByRelativePath -RelativePath $childRelativePath -LeafName $child.Name) {
            Remove-TargetPath -Path $child.FullName
        }
    }
}

if (-not (Test-Path -LiteralPath $resolvedTargetRoot)) {
    Ensure-Directory -Path $resolvedTargetRoot
}

$itemsToSync = @(
    @{
        Type        = 'Directory'
        SourcePath  = Join-Path -Path $resolvedSourceRoot -ChildPath 'sources'
        TargetPath  = Join-Path -Path $resolvedTargetRoot -ChildPath 'sources'
        DisplayName = 'sources'
    },
    @{
        Type        = 'Directory'
        SourcePath  = Join-Path -Path $resolvedSourceRoot -ChildPath 'src'
        TargetPath  = Join-Path -Path $resolvedTargetRoot -ChildPath 'src'
        DisplayName = 'src'
    },
    @{
        Type        = 'File'
        SourcePath  = Join-Path -Path $resolvedSourceRoot -ChildPath 'pom.xml'
        TargetPath  = Join-Path -Path $resolvedTargetRoot -ChildPath 'pom.xml'
        DisplayName = 'pom.xml'
    }
)

Write-Host "SourceRoot : $resolvedSourceRoot"
Write-Host "TargetRoot : $resolvedTargetRoot"
if (-not $SkipExcludeFile) {
    if (-not [string]::IsNullOrWhiteSpace($resolvedExcludeFile) -and (Test-Path -LiteralPath $resolvedExcludeFile -PathType Leaf)) {
        Write-Host "ExcludeFile: $resolvedExcludeFile"
    }
    else {
        Write-Host 'ExcludeFile: <none>'
    }
}
else {
    Write-Host 'ExcludeFile: <skipped>'
}
if ($excludePatterns.Count -gt 0) {
    Write-Host "Exclude    : $($excludePatterns -join ', ')"
}
else {
    Write-Host 'Exclude    : <none>'
}

if ($CleanExcludedInTarget) {
    Write-Host '[clean] removing excluded paths already present in target'

    $targetItemsToClean = @(
        (Join-Path -Path $resolvedTargetRoot -ChildPath 'sources')
        (Join-Path -Path $resolvedTargetRoot -ChildPath 'src')
        (Join-Path -Path $resolvedTargetRoot -ChildPath 'pom.xml')
    )

    foreach ($targetItem in $targetItemsToClean) {
        Remove-ExcludedTargetItems -Path $targetItem
    }
}

foreach ($item in $itemsToSync) {
    if (-not (Test-Path -LiteralPath $item.SourcePath)) {
        throw "Path to sync does not exist: $($item.SourcePath)"
    }

    Write-Host "[sync] $($item.DisplayName)"

    if ($item.Type -eq 'Directory') {
        Copy-SourceDirectory -SourcePath $item.SourcePath -DestinationPath $item.TargetPath
        continue
    }

    Copy-SourceFile -SourcePath $item.SourcePath -DestinationPath $item.TargetPath
}

Write-Host ''
Write-Host 'Sync completed.'
Write-Host "CreatedDirs : $($script:Stats.CreatedDirs)"
Write-Host "CopiedFiles : $($script:Stats.CopiedFiles)"
Write-Host "Skipped     : $($script:Stats.Skipped)"
