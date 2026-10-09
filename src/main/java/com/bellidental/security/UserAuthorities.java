package com.bellidental.security;

import com.bellidental.persistence.entity.Role;
import com.bellidental.persistence.entity.RolePermission;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.entity.UserRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Componente para extraer y formatear roles y permisos de un usuario.
 */
@Component
public class UserAuthorities {

    public List<SimpleGrantedAuthority> getAuthorities(User user) {
        Set<String> authorityStrings = new HashSet<>();
        for (UserRole userRole : user.getUserRoles()) {
            Role role = userRole.getRole();
            authorityStrings.add("ROLE_" + role.getCode());
            for (RolePermission rp : role.getRolePermissions()) {
                authorityStrings.add(rp.getPermission().getCode());
            }
        }
        return authorityStrings.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    public List<String> getRoles(User user) {
        return user.getUserRoles().stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toList());
    }

    public List<String> getPermissions(User user) {
        return user.getUserRoles().stream()
                .flatMap(ur -> ur.getRole().getRolePermissions().stream())
                .map(rp -> rp.getPermission().getCode())
                .distinct()
                .collect(Collectors.toList());
    }
}
