package com.prabhix.platform.site.service;

import com.prabhix.platform.site.domain.SiteEnums;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LeadInterestParserTest {

    @Test
    void acceptsEnumName() {
        var parsed = LeadInterestParser.parse("PLATFORM");
        assertEquals(SiteEnums.LeadInterest.PLATFORM, parsed.canonical());
        assertNull(parsed.rawLabel());
    }

    @Test
    void acceptsHumanLabel() {
        var parsed = LeadInterestParser.parse("Platform demo");
        assertEquals(SiteEnums.LeadInterest.PLATFORM, parsed.canonical());
    }

    @Test
    void preservesUnknownLabelAsOther() {
        var parsed = LeadInterestParser.parse("Something bespoke");
        assertEquals(SiteEnums.LeadInterest.OTHER, parsed.canonical());
        assertEquals("Something bespoke", parsed.rawLabel());
    }

    @Test
    void acceptsStarterPlanLabel() {
        var parsed = LeadInterestParser.parse("Starter plan");
        assertEquals(SiteEnums.LeadInterest.PLATFORM, parsed.canonical());
    }
}
