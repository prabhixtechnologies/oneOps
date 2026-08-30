package com.prabhix.platform.push.provider;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.push.config.PushProperties;
import com.prabhix.platform.push.domain.PushEnums;
import com.prabhix.platform.push.provider.apns.ApnsPushProvider;
import com.prabhix.platform.push.provider.fcm.FcmPushProvider;
import com.prabhix.platform.push.provider.noop.LoggingPushProvider;
import org.springframework.stereotype.Component;

@Component
public class PushProviderRouter {

    private final PushProperties properties;
    private final ObjectMapper objectMapper;
    private final PushProvider defaultProvider;
    private FcmPushProvider fcm;
    private ApnsPushProvider apns;

    public PushProviderRouter(PushProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.defaultProvider = selectConfigured();
    }

    public PushProvider select() {
        return defaultProvider;
    }

    public PushProvider select(PushEnums.Platform platform) {
        if (platform == PushEnums.Platform.FCM && useFcm()) {
            return fcm();
        }
        if (platform == PushEnums.Platform.APNS && useApns()) {
            return apns();
        }
        return new LoggingPushProvider();
    }

    private PushProvider selectConfigured() {
        if ("FCM".equalsIgnoreCase(properties.provider()) && properties.fcm().configured()) {
            return fcm();
        }
        if ("APNS".equalsIgnoreCase(properties.provider()) && properties.apns().configured()) {
            return apns();
        }
        return new LoggingPushProvider();
    }

    private boolean useFcm() {
        return properties.fcm().configured()
                && !"APNS".equalsIgnoreCase(properties.provider());
    }

    private boolean useApns() {
        return properties.apns().configured()
                && !"FCM".equalsIgnoreCase(properties.provider());
    }

    private FcmPushProvider fcm() {
        if (fcm == null) {
            fcm = new FcmPushProvider(properties.fcm(), objectMapper);
        }
        return fcm;
    }

    private ApnsPushProvider apns() {
        if (apns == null) {
            apns = new ApnsPushProvider(properties.apns(), objectMapper);
        }
        return apns;
    }
}
