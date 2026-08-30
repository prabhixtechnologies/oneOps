-- A purpose for a file somebody simply uploaded.
--
-- Every value in the original list said what a file was *for*: a mail attachment, an avatar, an
-- import job's payload. There was no value for "a document in the file library", so the file
-- library's uploads were recorded as MAIL_ATTACHMENT because that was the endpoint's default. Now
-- that mail is a separate product, that label is not merely imprecise — it names a feature this
-- application no longer has, and any retention or clean-up rule keyed on it would act on the
-- wrong rows.
--
-- Existing MAIL_ATTACHMENT rows are deliberately left alone. Some of them really are mail
-- attachments, and this migration has no way to tell which; guessing would corrupt the ones it
-- got wrong. The distinction starts being recorded from here.

ALTER TABLE public.stored_files
    DROP CONSTRAINT ck_stored_files_purpose;

ALTER TABLE public.stored_files
    ADD CONSTRAINT ck_stored_files_purpose CHECK (
        (purpose)::text = ANY ((ARRAY[
            'MAIL_ATTACHMENT'::character varying,
            'MAIL_RAW_MIME'::character varying,
            'AVATAR'::character varying,
            'LOGO'::character varying,
            'EXPORT'::character varying,
            'IMPORT'::character varying,
            'INVOICE'::character varying,
            'CHAT_ATTACHMENT'::character varying,
            'DOCUMENT'::character varying
        ])::text[])
    );
