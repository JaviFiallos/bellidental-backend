package com.bellidental.security;

import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga el usuario desde la base de datos para Spring Security.
 * Construye las GrantedAuthorities a partir de los roles asignados y sus permisos.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserAuthorities userAuthorities;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Use email for authentication as requested
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));

        if (!user.isActive() || user.isDeleted() || user.isSystem()) {
            throw new UsernameNotFoundException("Credenciales inválidas");
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(userAuthorities.getAuthorities(user))
                .build();
    }
}
