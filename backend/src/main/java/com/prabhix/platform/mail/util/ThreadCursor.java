package com.prabhix.platform.mail.util;

import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.mail.domain.MailThread;

/** Keyset cursor for thread lists: (last_message_at desc, id desc). */
public final class ThreadCursor {

    private ThreadCursor() {
    }

    public static String encode(MailThread thread) {
        return Cursor.of(thread.getLastMessageAt(), thread.getId()).encode();
    }

    public static Cursor decode(String encoded) {
        return Cursor.decode(encoded);
    }
}
