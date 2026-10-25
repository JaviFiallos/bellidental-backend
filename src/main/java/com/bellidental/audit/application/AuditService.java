package com.bellidental.audit.application;

import com.bellidental.audit.api.dto.AuditEntryResponse;
import com.bellidental.audit.api.dto.FieldChangeResponse;
import com.bellidental.audit.domain.AuditDiff;
import com.bellidental.persistence.entity.AuditRevision;
import com.bellidental.persistence.entity.Appointment;
import com.bellidental.persistence.entity.ClinicalRecord;
import com.bellidental.persistence.entity.DentalFinding;
import com.bellidental.persistence.entity.Encounter;
import com.bellidental.persistence.entity.EncounterDiagnosis;
import com.bellidental.persistence.entity.MedicalHistoryEntry;
import com.bellidental.persistence.entity.Medication;
import com.bellidental.persistence.entity.MedicationPreset;
import com.bellidental.persistence.entity.Patient;
import com.bellidental.persistence.entity.Payment;
import com.bellidental.persistence.entity.PerformedTreatment;
import com.bellidental.persistence.entity.Prescription;
import com.bellidental.persistence.entity.PrescriptionItem;
import com.bellidental.persistence.entity.Specialty;
import com.bellidental.persistence.entity.Treatment;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.AuditQueryRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.RevisionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    public static final Map<String, Class<?>> AUDITED_ENTITIES = Map.ofEntries(
            Map.entry("clinical-records", ClinicalRecord.class),
            Map.entry("medical-history-entries", MedicalHistoryEntry.class),
            Map.entry("encounters", Encounter.class),
            Map.entry("encounter-diagnoses", EncounterDiagnosis.class),
            Map.entry("dental-findings", DentalFinding.class),
            Map.entry("performed-treatments", PerformedTreatment.class),
            Map.entry("prescriptions", Prescription.class),
            Map.entry("prescription-items", PrescriptionItem.class),
            Map.entry("payments", Payment.class),
            Map.entry("appointments", Appointment.class),
            Map.entry("patients", Patient.class),
            Map.entry("treatments", Treatment.class),
            Map.entry("specialties", Specialty.class),
            Map.entry("medications", Medication.class),
            Map.entry("medication-presets", MedicationPreset.class)
    );

    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final AuditQueryRepository auditQueryRepository;

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> findHistory(String entityName, String id) {
        Class<?> entityClass = AUDITED_ENTITIES.get(entityName);
        if (entityClass == null) {
            throw new ResourceNotFoundException("Entidad no auditada: " + entityName);
        }

        AuditReader reader = AuditReaderFactory.get(entityManager);
        List<?> rows = reader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(parseId(entityClass, id)))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<AuditEntryResponse> entries = new ArrayList<>();
        Map<String, String> previousValues = Map.of();
        for (Object row : rows) {
            Object[] columns = (Object[]) row;
            Object entity = columns[0];
            AuditRevision revision = (AuditRevision) columns[1];
            RevisionType type = (RevisionType) columns[2];

            Map<String, String> currentValues = entity == null ? Map.of() : snapshot(entity);
            List<FieldChangeResponse> changes = AuditDiff.diff(previousValues, currentValues).stream()
                    .map(change -> new FieldChangeResponse(change.field(), change.oldValue(), change.newValue()))
                    .toList();
            entries.add(new AuditEntryResponse(
                    revision.getId(),
                    revision.getAt(),
                    revision.getUserId(),
                    findUsername(revision.getUserId()),
                    type.name(),
                    changes,
                    entityName,
                    id
            ));
            previousValues = currentValues;
        }
        return entries;
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> findEncounterFullHistory(UUID encounterId) {
        if (!auditQueryRepository.existsEncounterAudit(encounterId)) {
            throw new ResourceNotFoundException("Atención no encontrada en auditoría: " + encounterId);
        }

        List<AuditEntryResponse> allEntries = new ArrayList<>();
        allEntries.addAll(findHistory("encounters", encounterId.toString()));

        for (UUID diagId : auditQueryRepository.findEncounterDiagnosisIds(encounterId)) {
            allEntries.addAll(findHistory("encounter-diagnoses", diagId.toString()));
        }
        for (UUID dfId : auditQueryRepository.findDentalFindingIds(encounterId)) {
            allEntries.addAll(findHistory("dental-findings", dfId.toString()));
        }
        for (UUID ptId : auditQueryRepository.findPerformedTreatmentIds(encounterId)) {
            allEntries.addAll(findHistory("performed-treatments", ptId.toString()));
        }
        List<UUID> rxIds = auditQueryRepository.findPrescriptionIds(encounterId);
        for (UUID rxId : rxIds) {
            allEntries.addAll(findHistory("prescriptions", rxId.toString()));
        }
        if (!rxIds.isEmpty()) {
            for (UUID itemId : auditQueryRepository.findPrescriptionItemIds(rxIds)) {
                allEntries.addAll(findHistory("prescription-items", itemId.toString()));
            }
        }

        allEntries.sort(Comparator.comparing(AuditEntryResponse::at).thenComparing(AuditEntryResponse::revision));
        return allEntries;
    }

    /** Convierte el id de la URL al tipo de la clave primaria de la entidad (bigint o uuid). */
    private Object parseId(Class<?> entityClass, String id) {
        Class<?> idType = entityManager.getMetamodel().entity(entityClass).getIdType().getJavaType();
        try {
            return Long.class.equals(idType) ? Long.valueOf(id) : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Identificador no válido: " + id);
        }
    }

    private Map<String, String> snapshot(Object entity) {
        Object target = Hibernate.unproxy(entity);
        Map<String, String> values = new HashMap<>();
        for (Field field : target.getClass().getDeclaredFields()) {
            if (isSkipped(field)) {
                continue;
            }
            field.setAccessible(true);
            try {
                values.put(field.getName(), toText(field, field.get(target)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("No se pudo leer el campo auditado " + field.getName(), e);
            }
        }
        return values;
    }

    private boolean isSkipped(Field field) {
        return Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(NotAudited.class);
    }

    private String toText(Field field, Object value) {
        if (value == null) {
            return null;
        }
        if (value.getClass().isArray()) {
            return Arrays.toString((Object[]) value);
        }
        if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
            return String.valueOf(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(value));
        }
        return String.valueOf(value);
    }

    private String findUsername(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getUsername).orElse(null);
    }
}
