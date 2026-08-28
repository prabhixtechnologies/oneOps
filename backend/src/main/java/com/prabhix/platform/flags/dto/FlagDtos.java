package com.prabhix.platform.flags.dto;

import java.util.List;
import java.util.Map;

public final class FlagDtos {

    private FlagDtos() {
    }

    public record EffectiveFlags(Map<String, Boolean> flags) {
    }

    public record FlagDetail(String key, boolean enabled, String source, String description) {
    }

    public record EffectiveFlagsDetailed(List<FlagDetail> flags) {
    }

    public record SetOverrideRequest(boolean enabled, String reason) {
    }
}
