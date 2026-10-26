package com.bellidental.security;

import com.bellidental.persistence.entity.AuditRevision;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Guarda en cada revisión de Envers el usuario autenticado (null en jobs sin usuario). */
public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevision revision = (AuditRevision) revisionEntity;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser.CurrentUserPrincipal principal) {
            revision.setUserId(principal.userId());
        }
    }
}
