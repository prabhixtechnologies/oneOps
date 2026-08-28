package com.prabhix.platform.observability.context;

/**
 * MDC field names echoed into JSON logs and propagated across async boundaries.
 */
public final class MdcKeys {

    public static final String REQUEST_ID = "requestId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String TRACE_ID = "traceId";
    public static final String ORG_ID = "orgId";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    public static final String CLIENT_IP = "clientIp";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String HTTP_PATH = "httpPath";
    public static final String ROUTE_TEMPLATE = "routeTemplate";
    public static final String USER_AGENT = "userAgent";
    public static final String EVENT_CODE = "eventCode";

    private MdcKeys() {
    }
}
