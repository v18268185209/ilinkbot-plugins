ALTER TABLE wcf_peer_context
    ADD COLUMN context_status VARCHAR(64) NOT NULL DEFAULT 'INVALID' AFTER context_token,
    ADD COLUMN last_inbound_at DATETIME NULL AFTER last_message_at,
    ADD COLUMN reply_window_expires_at DATETIME NULL AFTER last_inbound_at,
    ADD COLUMN window_status VARCHAR(64) NOT NULL DEFAULT 'CLOSED' AFTER reply_window_expires_at;

UPDATE wcf_peer_context
   SET last_inbound_at = COALESCE(last_inbound_at, last_message_at),
       reply_window_expires_at = CASE
           WHEN COALESCE(last_inbound_at, last_message_at) IS NULL THEN reply_window_expires_at
           ELSE DATE_ADD(COALESCE(last_inbound_at, last_message_at), INTERVAL 24 HOUR)
       END,
       window_status = CASE
           WHEN COALESCE(last_inbound_at, last_message_at) IS NULL THEN 'CLOSED'
           WHEN DATE_ADD(COALESCE(last_inbound_at, last_message_at), INTERVAL 24 HOUR) <= NOW() THEN 'CLOSED'
           WHEN DATE_ADD(COALESCE(last_inbound_at, last_message_at), INTERVAL 24 HOUR) <= DATE_ADD(NOW(), INTERVAL 2 HOUR) THEN 'CLOSING_SOON'
           ELSE 'OPEN'
       END,
       context_status = CASE
           WHEN context_token IS NULL OR context_token = '' THEN 'INVALID'
           WHEN COALESCE(last_inbound_at, last_message_at) IS NULL THEN 'INVALID'
           WHEN DATE_ADD(COALESCE(last_inbound_at, last_message_at), INTERVAL 24 HOUR) <= NOW() THEN 'EXPIRED'
           ELSE 'ACTIVE'
       END;
