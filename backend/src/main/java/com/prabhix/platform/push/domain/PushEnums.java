package com.prabhix.platform.push.domain;

public final class PushEnums {

    private PushEnums() {
    }

    public enum Platform {
        FCM, APNS
    }

    public enum OutboxStatus {
        PENDING, CLAIMED, SENDING, SENT, FAILED, DEAD
    }
}
