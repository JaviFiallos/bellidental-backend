package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.PrivacyConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, UUID> {

    boolean existsByPhoneE164AndNoticeVersion(String phoneE164, String noticeVersion);

    Optional<PrivacyConsent> findFirstByPhoneE164AndNoticeVersionOrderByAcceptedAtDesc(String phoneE164, String noticeVersion);

    List<PrivacyConsent> findByPhoneE164(String phoneE164);
}
