package com.prabhix.platform.visitor.domain;

public final class VisitorEnums {

    private VisitorEnums() {
    }

    public enum ConsentStatus {
        FULL, MINIMAL, DELETED
    }

    public enum AggregateMetric {
        PAGE_VIEWS, SESSIONS, UNIQUE_VISITORS, EVENT
    }
}
