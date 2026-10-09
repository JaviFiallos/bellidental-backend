package com.bellidental.security;

import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filtro JWT — intercepta cada request, extrae y valida el token Bearer
 * y establece la autenticación en el SecurityContext.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserAuthorities userAuthorities;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(BEARER_PREFIX.length());
        final String subjectIdStr;
        try {
            subjectIdStr = jwtService.extractSubject(jwt);
        } catch (JwtException e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (subjectIdStr != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            
            if (jwtService.isTokenValid(jwt, subjectIdStr)) {
                UUID userId = UUID.fromString(subjectIdStr);
                Optional<User> userOpt = userRepository.findWithRolesByIdAndDeletedAtIsNull(userId);
                
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (user.isActive() && !user.isSystem()) {

                        String profIdStr = jwtService.extractClaim(jwt, claims -> claims.get("professionalId", String.class));
                        Optional<UUID> professionalId = profIdStr != null ? Optional.of(UUID.fromString(profIdStr)) : Optional.empty();

                        CurrentUser.CurrentUserPrincipal principal = new CurrentUser.CurrentUserPrincipal(userId, professionalId);

                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                userAuthorities.getAuthorities(user)
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
