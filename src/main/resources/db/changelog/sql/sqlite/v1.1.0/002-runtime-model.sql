ALTER TABLE wcf_wechat_account ADD COLUMN bind_status TEXT NOT NULL DEFAULT 'UNBOUND';
ALTER TABLE wcf_wechat_account ADD COLUMN current_runtime_id INTEGER;
ALTER TABLE wcf_wechat_account ADD COLUMN last_bind_at TEXT;

ALTER TABLE wcf_login_session ADD COLUMN confirmed_runtime_id INTEGER;

CREATE TABLE IF NOT EXISTS wcf_bot_runtime (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wechat_account_id INTEGER NOT NULL,
    runtime_type TEXT NOT NULL DEFAULT 'ILINK',
    base_url TEXT DEFAULT '',
    bot_token TEXT,
    ilink_user_id TEXT DEFAULT '',
    runtime_status TEXT NOT NULL DEFAULT 'INIT',
    expires_at TEXT,
    last_heartbeat_at TEXT,
    last_online_at TEXT,
    last_offline_at TEXT,
    replace_by_runtime_id INTEGER,
    is_active INTEGER NOT NULL DEFAULT 1,
    last_error TEXT,
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_wcf_bot_runtime_account ON wcf_bot_runtime(wechat_account_id);
CREATE INDEX IF NOT EXISTS idx_wcf_bot_runtime_active ON wcf_bot_runtime(wechat_account_id, is_active);

CREATE TABLE IF NOT EXISTS wcf_message_dispatch (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wechat_account_id INTEGER NOT NULL,
    runtime_id INTEGER,
    peer_user_id TEXT DEFAULT '',
    dispatch_type TEXT NOT NULL,
    payload_json TEXT,
    dispatch_status TEXT NOT NULL DEFAULT 'CREATED',
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    source_type TEXT DEFAULT '',
    source_id TEXT DEFAULT '',
    trace_id TEXT DEFAULT '',
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_wcf_dispatch_account_time ON wcf_message_dispatch(wechat_account_id, create_time);
CREATE INDEX IF NOT EXISTS idx_wcf_dispatch_trace ON wcf_message_dispatch(trace_id);

CREATE TABLE IF NOT EXISTS wcf_media_asset (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wechat_account_id INTEGER NOT NULL,
    event_id INTEGER,
    dispatch_id INTEGER,
    asset_type TEXT NOT NULL,
    storage_path TEXT DEFAULT '',
    file_name TEXT DEFAULT '',
    mime_type TEXT DEFAULT '',
    sha256 TEXT DEFAULT '',
    download_status TEXT NOT NULL DEFAULT 'READY',
    error_message TEXT,
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_wcf_media_asset_account ON wcf_media_asset(wechat_account_id);
CREATE INDEX IF NOT EXISTS idx_wcf_media_asset_event ON wcf_media_asset(event_id);
CREATE INDEX IF NOT EXISTS idx_wcf_media_asset_dispatch ON wcf_media_asset(dispatch_id);
