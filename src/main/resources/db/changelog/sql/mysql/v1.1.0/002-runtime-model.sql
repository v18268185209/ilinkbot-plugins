ALTER TABLE wcf_wechat_account
    ADD COLUMN bind_status VARCHAR(64) NOT NULL DEFAULT 'UNBOUND' AFTER owner_user_id,
    ADD COLUMN current_runtime_id BIGINT NULL AFTER bind_status,
    ADD COLUMN last_bind_at DATETIME NULL AFTER current_runtime_id;

ALTER TABLE wcf_login_session
    ADD COLUMN confirmed_runtime_id BIGINT NULL AFTER wechat_account_id;

CREATE TABLE IF NOT EXISTS wcf_bot_runtime (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wechat_account_id BIGINT NOT NULL,
    runtime_type VARCHAR(64) NOT NULL DEFAULT 'ILINK',
    base_url VARCHAR(512) DEFAULT '',
    bot_token TEXT,
    ilink_user_id VARCHAR(255) DEFAULT '',
    runtime_status VARCHAR(64) NOT NULL DEFAULT 'INIT',
    expires_at DATETIME NULL,
    last_heartbeat_at DATETIME NULL,
    last_online_at DATETIME NULL,
    last_offline_at DATETIME NULL,
    replace_by_runtime_id BIGINT NULL,
    is_active INT NOT NULL DEFAULT 1,
    last_error TEXT,
    create_user_id BIGINT NULL,
    update_user_id BIGINT NULL,
    company_id BIGINT NULL,
    dept_id BIGINT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    is_deleted INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 1,
    KEY idx_wcf_bot_runtime_account (wechat_account_id),
    KEY idx_wcf_bot_runtime_active (wechat_account_id, is_active)
);

CREATE TABLE IF NOT EXISTS wcf_message_dispatch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wechat_account_id BIGINT NOT NULL,
    runtime_id BIGINT NULL,
    peer_user_id VARCHAR(255) DEFAULT '',
    dispatch_type VARCHAR(64) NOT NULL,
    payload_json LONGTEXT,
    dispatch_status VARCHAR(64) NOT NULL DEFAULT 'CREATED',
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    source_type VARCHAR(64) DEFAULT '',
    source_id VARCHAR(255) DEFAULT '',
    trace_id VARCHAR(255) DEFAULT '',
    create_user_id BIGINT NULL,
    update_user_id BIGINT NULL,
    company_id BIGINT NULL,
    dept_id BIGINT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    is_deleted INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 1,
    KEY idx_wcf_dispatch_account_time (wechat_account_id, create_time),
    KEY idx_wcf_dispatch_trace (trace_id)
);

CREATE TABLE IF NOT EXISTS wcf_media_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wechat_account_id BIGINT NOT NULL,
    event_id BIGINT NULL,
    dispatch_id BIGINT NULL,
    asset_type VARCHAR(64) NOT NULL,
    storage_path VARCHAR(1024) DEFAULT '',
    file_name VARCHAR(512) DEFAULT '',
    mime_type VARCHAR(255) DEFAULT '',
    sha256 VARCHAR(128) DEFAULT '',
    download_status VARCHAR(64) NOT NULL DEFAULT 'READY',
    error_message TEXT,
    create_user_id BIGINT NULL,
    update_user_id BIGINT NULL,
    company_id BIGINT NULL,
    dept_id BIGINT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    is_deleted INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 1,
    KEY idx_wcf_media_asset_account (wechat_account_id),
    KEY idx_wcf_media_asset_event (event_id),
    KEY idx_wcf_media_asset_dispatch (dispatch_id)
);
