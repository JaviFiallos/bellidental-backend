package com.bellidental.security;

import com.bellidental.config.BotProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BotApiKeyFilter extends OncePerRequestFilter {

    private static final String BOT_KEY_HEADER = "X-Bot-Key";
    private static final UUID BOT_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final BotProperties botProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/bot");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String headerKey = request.getHeader(BOT_KEY_HEADER);
        String configuredKey = botProperties.apiKey();

        if (headerKey != null && configuredKey != null && !configuredKey.isBlank()) {
            if (MessageDigest.isEqual(headerKey.getBytes(StandardCharsets.UTF_8), configuredKey.getBytes(StandardCharsets.UTF_8))) {
                CurrentUser.CurrentUserPrincipal principal = new CurrentUser.CurrentUserPrincipal(
                        BOT_USER_ID,
                        Optional.empty()
                );
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("BOT"))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
