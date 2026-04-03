CREATE TABLE IF NOT EXISTS wcf_webhook_delivery (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wechat_account_id INTEGER NOT NULL,
    event_id INTEGER,
    delivery_type TEXT NOT NULL,
    target_url TEXT DEFAULT '',
    request_body TEXT,
    response_body TEXT,
    response_code INTEGER,
    delivery_status TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 1,
    error_message TEXT,
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
CREATE INDEX IF NOT EXISTS idx_wcf_webhook_delivery_account ON wcf_webhook_delivery(wechat_account_id);
CREATE INDEX IF NOT EXISTS idx_wcf_webhook_delivery_event ON wcf_webhook_delivery(event_id);
CREATE INDEX IF NOT EXISTS idx_wcf_webhook_delivery_trace ON wcf_webhook_delivery(trace_id);
