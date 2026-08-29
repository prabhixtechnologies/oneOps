-- Mail that was written to a log file and recorded as delivered.
--
-- MailTransportRouter used to end every transport chain in LoggingTransport, and return it again as
-- a final fallback once the chain was exhausted. LoggingTransport.send() reports success, because
-- writing the line is all it claims to do, so OutboxWorker set status = 'SENT' and moved on. The
-- recipient got nothing. Password resets and email OTPs are the mail most affected, since they are
-- the ones production actually tried to send.
--
-- Marked DEAD rather than FAILED on purpose. FAILED is retryable, and these rows are old: releasing
-- a weeks-old password reset or a long-expired OTP into someone's inbox is worse than not sending
-- it. DEAD is terminal and states plainly that the message was never delivered, which is what a
-- support conversation needs to know.
--
-- sent_at is cleared for the same reason: it recorded the moment the message was logged, and
-- leaving it set would keep every "when did we email this customer" query wrong.
UPDATE mail_outbox
SET status     = 'DEAD',
    sent_at    = NULL,
    last_error = left(
        coalesce(last_error || ' | ', '')
            || 'Never delivered: recorded SENT by the logging transport fallback, corrected in V60.',
        2000),
    updated_at = now()
WHERE transport_used = 'LOGGING'
  AND status = 'SENT';

-- Delivery events say the same thing, and the helpdesk timeline reads from them rather than from
-- mail_outbox, so a row corrected above would still show "Sent" in the UI without this.
UPDATE mail_delivery_events
SET event_type = 'FAILED',
    detail     = left(
        coalesce(detail || ' | ', '')
            || 'Never delivered: logging transport fallback, corrected in V60.', 1000)
WHERE event_type = 'SENT'
  AND outbox_id IN (SELECT id FROM mail_outbox WHERE transport_used = 'LOGGING');
