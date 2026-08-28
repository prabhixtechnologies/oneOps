package com.prabhix.platform.site.service;

import com.prabhix.platform.site.domain.SiteEnums;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Accepts both canonical {@link SiteEnums.LeadInterest} names and the human-readable
 * labels the public marketing form sends.
 */
public final class LeadInterestParser {

    private static final Map<String, SiteEnums.LeadInterest> LABELS = Map.ofEntries(
            Map.entry("platform", SiteEnums.LeadInterest.PLATFORM),
            Map.entry("platform demo", SiteEnums.LeadInterest.PLATFORM),
            Map.entry("starter plan", SiteEnums.LeadInterest.PLATFORM),
            Map.entry("growth plan", SiteEnums.LeadInterest.PLATFORM),
            Map.entry("business plan", SiteEnums.LeadInterest.PLATFORM),
            Map.entry("enterprise", SiteEnums.LeadInterest.PLATFORM),
            Map.entry("mobistack", SiteEnums.LeadInterest.MOBISTACK),
            Map.entry("custom software", SiteEnums.LeadInterest.CUSTOM_SOFTWARE),
            Map.entry("custom software development", SiteEnums.LeadInterest.CUSTOM_SOFTWARE),
            Map.entry("cloud devops", SiteEnums.LeadInterest.CLOUD_DEVOPS),
            Map.entry("cloud & devops", SiteEnums.LeadInterest.CLOUD_DEVOPS),
            Map.entry("cloud and devops", SiteEnums.LeadInterest.CLOUD_DEVOPS),
            Map.entry("ai ml", SiteEnums.LeadInterest.AI_ML),
            Map.entry("ai / ml", SiteEnums.LeadInterest.AI_ML),
            Map.entry("ai & ml", SiteEnums.LeadInterest.AI_ML),
            Map.entry("ai and ml", SiteEnums.LeadInterest.AI_ML),
            Map.entry("mobile apps", SiteEnums.LeadInterest.MOBILE_APPS),
            Map.entry("mobile app", SiteEnums.LeadInterest.MOBILE_APPS),
            Map.entry("consulting", SiteEnums.LeadInterest.CONSULTING),
            Map.entry("partnership", SiteEnums.LeadInterest.PARTNERSHIP),
            Map.entry("other", SiteEnums.LeadInterest.OTHER));

    private LeadInterestParser() {
    }

    public record ParsedInterest(SiteEnums.LeadInterest canonical, String rawLabel) {
    }

    public static ParsedInterest parse(String input) {
        if (input == null || input.isBlank()) {
            return new ParsedInterest(SiteEnums.LeadInterest.OTHER, null);
        }
        String trimmed = input.trim();
        Optional<SiteEnums.LeadInterest> enumMatch = SiteEnums.LeadInterest.parse(trimmed);
        if (enumMatch.isPresent()) {
            return new ParsedInterest(enumMatch.get(), null);
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        SiteEnums.LeadInterest mapped = LABELS.get(normalized);
        if (mapped != null) {
            return new ParsedInterest(mapped, trimmed);
        }
        return new ParsedInterest(SiteEnums.LeadInterest.OTHER, trimmed);
    }
}
