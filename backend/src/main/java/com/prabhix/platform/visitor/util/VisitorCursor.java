package com.prabhix.platform.visitor.util;

import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.visitor.domain.Visitor;
import com.prabhix.platform.visitor.domain.VisitorEvent;
import com.prabhix.platform.visitor.domain.VisitorPageView;
import com.prabhix.platform.visitor.domain.VisitorSession;

public final class VisitorCursor {

    private VisitorCursor() {
    }

    public static String encodeVisitor(Visitor visitor) {
        return Cursor.of(visitor.getLastSeenAt(), visitor.getId()).encode();
    }

    public static String encodeSession(VisitorSession session) {
        return Cursor.of(session.getStartedAt(), session.getId()).encode();
    }

    public static String encodePageView(VisitorPageView view) {
        return Cursor.of(view.getViewedAt(), view.getId()).encode();
    }

    public static String encodeEvent(VisitorEvent event) {
        return Cursor.of(event.getOccurredAt(), event.getId()).encode();
    }
}
