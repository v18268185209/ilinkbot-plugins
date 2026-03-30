CREATE TABLE IF NOT EXISTS wcf_wechat_account (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_code TEXT NOT NULL UNIQUE,
    account_name TEXT NOT NULL,
    base_url TEXT DEFAULT '',
    bot_token TEXT,
    ilink_user_id TEXT DEFAULT '',
    login_status TEXT DEFAULT 'CREATED',
    poll_status TEXT DEFAULT 'STOPPED',
    last_error TEXT,
    get_updates_buf TEXT,
    last_poll_at TEXT,
    last_inbound_at TEXT,
    owner_user_id INTEGER,
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS wcf_wechat_account_member (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wechat_account_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    role_code TEXT NOT NULL,
    permission_scope TEXT DEFAULT 'FULL',
    is_primary_owner INTEGER NOT NULL DEFAULT 0,
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wcf_account_member ON wcf_wechat_account_member(wechat_account_id, user_id);

CREATE TABLE IF NOT EXISTS wcf_peer_context (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wechat_account_id INTEGER NOT NULL,
    peer_user_id TEXT NOT NULL,
    context_token TEXT NOT NULL,
    last_message_at TEXT,
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wcf_peer_context ON wcf_peer_context(wechat_account_id, peer_user_id);

CREATE TABLE IF NOT EXISTS wcf_login_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_code TEXT NOT NULL UNIQUE,
    base_url TEXT NOT NULL,
    qr_code_url TEXT NOT NULL,
    qr_code_content TEXT NOT NULL,
    session_status TEXT NOT NULL,
    wechat_account_id INTEGER,
    error_message TEXT,
    expired_at TEXT,
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS wcf_event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wechat_account_id INTEGER NOT NULL,
    direction TEXT NOT NULL,
    event_type TEXT NOT NULL,
    from_user_id TEXT DEFAULT '',
    to_user_id TEXT DEFAULT '',
    message_id INTEGER,
    context_token TEXT DEFAULT '',
    body_text TEXT,
    media_path TEXT DEFAULT '',
    media_file_name TEXT DEFAULT '',
    media_mime_type TEXT DEFAULT '',
    raw_json TEXT,
    owner_user_id INTEGER,
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS wcf_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wechat_account_id INTEGER,
    level TEXT NOT NULL,
    message TEXT NOT NULL,
    source TEXT NOT NULL,
    meta_json TEXT,
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS wcf_setting (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    config_group TEXT NOT NULL,
    config_key TEXT NOT NULL,
    config_value TEXT,
    config_type TEXT NOT NULL DEFAULT 'string',
    is_secret INTEGER NOT NULL DEFAULT 0,
    create_user_id INTEGER,
    update_user_id INTEGER,
    company_id INTEGER,
    dept_id INTEGER,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wcf_setting_group_key ON wcf_setting(config_group, config_key, company_id);
