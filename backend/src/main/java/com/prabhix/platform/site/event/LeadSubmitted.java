package com.prabhix.platform.site.event;

import com.prabhix.platform.common.event.PlatformEvent;

import java.util.UUID;

/** Published when a marketing-site lead is captured for downstream sales workflows. */
public record LeadSubmitted(UUID leadId, String email, String name) implements PlatformEvent {
}
