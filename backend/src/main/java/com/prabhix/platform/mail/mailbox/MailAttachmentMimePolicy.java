package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;

import java.util.Locale;
import java.util.Set;

/** Blocks content types that should never ride on mail compose uploads. */
final class MailAttachmentMimePolicy {

    private static final Set<String> BLOCKED = Set.of(
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/x-sh",
            "application/x-csh",
            "application/javascript",
            "text/javascript",
            "application/x-httpd-php",
            "application/vnd.microsoft.portable-executable",
            "application/x-executable");

    private MailAttachmentMimePolicy() {
    }

    static void assertAllowed(String contentType, String filename) {
        String type = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (BLOCKED.contains(type)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "That file type cannot be sent as a mail attachment");
        }
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".exe") || lower.endsWith(".bat") || lower.endsWith(".cmd")
                    || lower.endsWith(".com") || lower.endsWith(".scr") || lower.endsWith(".js")
                    || lower.endsWith(".vbs") || lower.endsWith(".ps1")) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                        "That filename extension cannot be sent as a mail attachment");
            }
        }
    }
}
