package com.prabhix.platform.visitor.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class VisitorBotFilter {

    private static final Pattern BOT = Pattern.compile(
            "bot|crawl|spider|slurp|mediapartners|facebookexternalhit|preview|headless",
            Pattern.CASE_INSENSITIVE);

    public boolean isBot(String userAgent) {
        return userAgent != null && BOT.matcher(userAgent).find();
    }
}
