ALTER TABLE wcf_peer_context ADD COLUMN context_status TEXT NOT NULL DEFAULT 'INVALID';
ALTER TABLE wcf_peer_context ADD COLUMN last_inbound_at TEXT;
ALTER TABLE wcf_peer_context ADD COLUMN reply_window_expires_at TEXT;
ALTER TABLE wcf_peer_context ADD COLUMN window_status TEXT NOT NULL DEFAULT 'CLOSED';

UPDATE wcf_peer_context
   SET last_inbound_at = COALESCE(last_inbound_at, last_message_at),
       reply_window_expires_at = CASE
           WHEN COALESCE(last_inbound_at, last_message_at) IS NULL THEN reply_window_expires_at
           ELSE datetime(COALESCE(last_inbound_at, last_message_at), '+24 hours')
       END,
       window_status = CASE
           WHEN COALESCE(last_inbound_at, last_message_at) IS NULL THEN 'CLOSED'
           WHEN datetime(COALESCE(last_inbound_at, last_message_at), '+24 hours') <= datetime('now') THEN 'CLOSED'
           WHEN datetime(COALESCE(last_inbound_at, last_message_at), '+24 hours') <= datetime('now', '+2 hours') THEN 'CLOSING_SOON'
           ELSE 'OPEN'
       END,
       context_status = CASE
           WHEN context_token IS NULL OR context_token = '' THEN 'INVALID'
           WHEN COALESCE(last_inbound_at, last_message_at) IS NULL THEN 'INVALID'
           WHEN datetime(COALESCE(last_inbound_at, last_message_at), '+24 hours') <= datetime('now') THEN 'EXPIRED'
           ELSE 'ACTIVE'
       END;
