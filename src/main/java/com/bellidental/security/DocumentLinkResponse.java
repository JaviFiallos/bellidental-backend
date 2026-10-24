package com.bellidental.security;

import java.time.OffsetDateTime;

public record DocumentLinkResponse(
        String url,
        OffsetDateTime expiresAt
) {}
