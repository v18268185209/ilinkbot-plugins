<template>
  <span class="field-hint">
    <span>{{ label }}</span>
    <n-tooltip trigger="hover">
      <template #trigger>
        <button type="button" class="field-hint__trigger">?</button>
      </template>
      <span class="field-hint__tip">{{ tip }}</span>
    </n-tooltip>
  </span>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  tip: { type: String, required: true }
})
</script>

<style scoped>
.field-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.field-hint__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  cursor: help;
}

.field-hint__tip {
  display: inline-block;
  max-width: 280px;
  line-height: 1.6;
}
</style>
