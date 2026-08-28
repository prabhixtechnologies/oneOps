package com.prabhix.platform.site.domain;

import java.util.Locale;

public final class SiteEnums {

    private SiteEnums() {
    }

    public enum LeadInterest {
        PLATFORM, MOBISTACK, CUSTOM_SOFTWARE, CLOUD_DEVOPS, AI_ML,
        MOBILE_APPS, CONSULTING, PARTNERSHIP, OTHER;

        public static java.util.Optional<LeadInterest> parse(String value) {
            if (value == null || value.isBlank()) {
                return java.util.Optional.empty();
            }
            try {
                return java.util.Optional.of(LeadInterest.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                return java.util.Optional.empty();
            }
        }
    }

    public enum LeadStatus {
        NEW, CONTACTED, QUALIFIED, DEMO_BOOKED, WON, LOST, SPAM
    }

    public enum SubscriberStatus {
        PENDING, CONFIRMED, UNSUBSCRIBED, BOUNCED
    }

    public enum JobEmploymentType {
        FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP
    }

    public enum JobWorkMode {
        ONSITE, HYBRID, REMOTE
    }

    public enum JobRoleStatus {
        OPEN, PAUSED, CLOSED
    }

    public enum ApplicationStatus {
        RECEIVED, SCREENING, INTERVIEWING, OFFERED, HIRED, REJECTED, WITHDRAWN
    }
}
