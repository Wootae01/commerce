package com.commerce.support;

import java.time.Duration;

public final class ProductCachePolicy {
    private ProductCachePolicy() {}

    public static final String FEATURED_KEY = "commerce:product:home:featured";
    public static final String PREFIX_POPULAR_KEY = "commerce:product:home:popular";              //  POPULAR_KEY + ":days" + days + ":top" + limit;
    public static final String FEATURED_LOCK_KEY = "commerce:product:home:featured:lock";
    public static final String PREFIX_POPULAR_LOCK_KEY = "commerce:product:home:popular:lock";

    public static final Duration FEATURED_TTL = Duration.ofDays(7);
    public static final Duration POPULAR_TTL  = Duration.ofHours(1);
    public static final Duration NULL_TTL     = Duration.ofMinutes(2);

    public static final long FEATURED_LOCK_TTL_MS = 400;         // 이름도 명확히
    public static final long POPULAR_LOCK_TTL_MS = 500;         // 이름도 명확히
    public static final int MAX_RETRY = 40;
    public static final long RETRY_DELAY_MS = 15;
    public static final long RETRY_JITTER_MS = 15;
}
