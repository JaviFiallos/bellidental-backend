package com.bellidental.identity;

import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class BaseSeedIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfessionalSpecialtyRepository professionalSpecialtyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedUsers_afterMigration_haveExactRoles() {
        User director = userRepository.findByUsernameAndDeletedAtIsNull("director").orElseThrow();
        assertThat(director.getEmail()).isEqualTo("director@bellidental.local");
        assertThat(director.isSystem()).isFalse();
        assertThat(getRoleCodes(director)).containsExactlyInAnyOrder("ADMIN", "ODONTOLOGO");

        User odontologa = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        assertThat(odontologa.getEmail()).isEqualTo("odontologa@bellidental.local");
        assertThat(odontologa.isSystem()).isFalse();
        assertThat(getRoleCodes(odontologa)).containsExactly("ODONTOLOGO");

        User secretaria = userRepository.findByUsernameAndDeletedAtIsNull("secretaria").orElseThrow();
        assertThat(secretaria.getEmail()).isEqualTo("secretaria@bellidental.local");
        assertThat(secretaria.isSystem()).isFalse();
        assertThat(getRoleCodes(secretaria)).containsExactly("SECRETARIA");

        User bot = userRepository.findByUsernameAndDeletedAtIsNull("bot_whatsapp").orElseThrow();
        assertThat(bot.getEmail()).isEqualTo("bot@bellidental.local");
        assertThat(bot.isSystem()).isTrue();
        assertThat(getRoleCodes(bot)).isEmpty();
    }

    @Test
    void seedPasswords_temporaryPassword_matchesEncoder() {
        User director = userRepository.findByUsernameAndDeletedAtIsNull("director").orElseThrow();
        assertThat(passwordEncoder.matches("Cambiar.2026!", director.getPasswordHash())).isTrue();

        User odontologa = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        assertThat(passwordEncoder.matches("Cambiar.2026!", odontologa.getPasswordHash())).isTrue();

        User secretaria = userRepository.findByUsernameAndDeletedAtIsNull("secretaria").orElseThrow();
        assertThat(passwordEncoder.matches("Cambiar.2026!", secretaria.getPasswordHash())).isTrue();

        User bot = userRepository.findByUsernameAndDeletedAtIsNull("bot_whatsapp").orElseThrow();
        assertThat(bot.getPasswordHash()).isEqualTo("!"); // Bot has no valid password
    }

    @Test
    void rolePermissions_secretariaAndOdontologo_matchMatrix() {
        User odontologa = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        Set<String> odontologaPerms = getPermissionCodes(odontologa);
        
        assertThat(odontologaPerms).doesNotContainAnyElementsOf(
                List.of("SCHEDULE_MANAGE", "PATIENT_CREATE", "PATIENT_UPDATE")
        );
        assertThat(odontologaPerms).contains("RECORD_CREATE", "RECORD_READ", "RECORD_UPDATE", "APPOINTMENT_CREATE", "BILLING_CREATE", "BILLING_READ");

        User secretaria = userRepository.findByUsernameAndDeletedAtIsNull("secretaria").orElseThrow();
        Set<String> secretariaPerms = getPermissionCodes(secretaria);

        assertThat(secretariaPerms).doesNotContainAnyElementsOf(
                List.of("RECORD_CREATE", "RECORD_READ", "RECORD_UPDATE", "DASHBOARD_VIEW", "PROFESSIONAL_MANAGE")
        );
        assertThat(secretariaPerms).contains("SCHEDULE_MANAGE", "BILLING_CREATE", "BILLING_READ", "PATIENT_CREATE");
    }

    @Test
    void seedProfessionals_directorAndOdontologa_haveSpecialty() {
        User director = userRepository.findByUsernameAndDeletedAtIsNull("director").orElseThrow();
        Optional<Professional> profDirector = professionalRepository.findByUserId(director.getId());
        assertThat(profDirector).isPresent();
        assertThat(professionalSpecialtyRepository.findByProfessionalId(profDirector.get().getId())).isNotEmpty();

        User odontologa = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        Optional<Professional> profOdontologa = professionalRepository.findByUserId(odontologa.getId());
        assertThat(profOdontologa).isPresent();
        assertThat(professionalSpecialtyRepository.findByProfessionalId(profOdontologa.get().getId())).isNotEmpty();

        User secretaria = userRepository.findByUsernameAndDeletedAtIsNull("secretaria").orElseThrow();
        assertThat(professionalRepository.findByUserId(secretaria.getId())).isEmpty();

        User bot = userRepository.findByUsernameAndDeletedAtIsNull("bot_whatsapp").orElseThrow();
        assertThat(professionalRepository.findByUserId(bot.getId())).isEmpty();
    }

    private Set<String> getRoleCodes(User user) {
        return user.getUserRoles().stream()
                .map(r -> r.getRole().getCode())
                .collect(Collectors.toSet());
    }

    private Set<String> getPermissionCodes(User user) {
        return user.getUserRoles().stream()
                .flatMap(ur -> ur.getRole().getRolePermissions().stream())
                .map(rp -> rp.getPermission().getCode())
                .collect(Collectors.toSet());
    }
}
