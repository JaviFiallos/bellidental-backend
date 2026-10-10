package com.bellidental.odontogram.application;

import com.bellidental.clinical.ClinicalDirectory;
import com.bellidental.clinical.EncounterSummary;
import com.bellidental.odontogram.api.dto.*;
import com.bellidental.odontogram.domain.*;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.persistence.entity.*;
import com.bellidental.persistence.repository.*;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OdontogramService {

    private final ToothRepository toothRepository;
    private final ToothSurfaceRepository toothSurfaceRepository;
    private final DentalConditionRepository conditionRepository;
    private final DentalConditionSymbolRepository symbolRepository;
    private final DentalFindingRepository findingRepository;

    private final ClinicalDirectory clinicalDirectory;
    private final PatientDirectory patientDirectory;
    private final ClinicClock clinicClock;

    @Transactional(readOnly = true)
    public CatalogResponse getCatalog() {
        List<CatalogResponse.ToothDto> teeth = toothRepository.findAll(Sort.by("toothCode")).stream()
                .map(t -> new CatalogResponse.ToothDto(t.getToothCode(), t.getDentition(), t.getArch(), t.getQuadrant(), t.getPosition(), t.getToothType()))
                .toList();

        List<CatalogResponse.SurfaceDto> surfaces = toothSurfaceRepository.findAll().stream()
                .map(s -> new CatalogResponse.SurfaceDto(s.getSurfaceCode(), s.getName()))
                .toList();

        List<DentalConditionSymbol> symbols = symbolRepository.findAll();
        Map<Long, List<SymbolResponse>> symbolsByCondition = symbols.stream()
                .collect(Collectors.groupingBy(
                        DentalConditionSymbol::getDentalConditionId,
                        Collectors.mapping(s -> new SymbolResponse(s.getDentalConditionSymbolId(), s.getStatus(), s.getShape(), s.getColor(), s.getSecondaryColor(), s.getFilled(), s.getLineStyle()), Collectors.toList())
                ));

        List<CatalogResponse.ConditionDto> conditions = conditionRepository.findAll().stream()
                .filter(DentalCondition::getActive)
                .map(c -> new CatalogResponse.ConditionDto(
                        c.getDentalConditionId(), c.getCode(), c.getName(), c.getCategory(), c.getScope(),
                        c.getGradeMin(), c.getGradeMax(), symbolsByCondition.getOrDefault(c.getDentalConditionId(), List.of())
                ))
                .toList();

        return new CatalogResponse(teeth, surfaces, conditions);
    }

    @Transactional
    public FindingResponse createFinding(UUID encounterId, CreateFindingRequest request) {
        clinicalDirectory.requireModifiableEncounter(encounterId);

        DentalCondition condition = conditionRepository.findById(request.conditionId())
                .filter(DentalCondition::getActive)
                .orElseThrow(() -> new InvalidInputException("Condición inactiva o no existe"));

        Tooth tooth = toothRepository.findById(request.toothCode())
                .orElseThrow(() -> new InvalidInputException("Pieza no existe"));

        Tooth endTooth = null;
        if (request.endToothCode() != null) {
            endTooth = toothRepository.findById(request.endToothCode())
                    .orElseThrow(() -> new InvalidInputException("Pieza final no existe"));
        }

        if (request.surfaceCode() != null && !toothSurfaceRepository.existsById(request.surfaceCode())) {
            throw new InvalidInputException("Cara no existe");
        }

        boolean hasSymbol = symbolRepository.existsByDentalConditionIdAndStatus(condition.getDentalConditionId(), request.status());

        FindingRules.validate(
                condition.getScope(), condition.getGradeMin(), condition.getGradeMax(), condition.getCode(),
                tooth.getArch(), tooth.getToothCode(),
                endTooth != null ? endTooth.getArch() : null, request.endToothCode(),
                request.surfaceCode(), request.grade(), request.notes(), hasSymbol
        );

        DentalFinding finding = new DentalFinding();
        finding.setEncounterId(encounterId);
        finding.setToothCode(request.toothCode());
        finding.setEndToothCode(request.endToothCode());
        finding.setSurfaceCode(request.surfaceCode());
        finding.setDentalConditionId(request.conditionId());
        finding.setStatus(request.status());
        finding.setGrade(request.grade());
        finding.setNotes(request.notes());
        finding.setCreatedAt(clinicClock.now());

        finding = findingRepository.save(finding);
        return mapToResponses(List.of(finding)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<FindingResponse> getEncounterFindings(UUID encounterId) {
        clinicalDirectory.findEncounter(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Atención no encontrada"));

        List<DentalFinding> findings = findingRepository.findByEncounterIdAndVoidedAtIsNull(encounterId);
        return mapToResponses(findings);
    }

    @Transactional
    public void voidFinding(UUID findingId) {
        DentalFinding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new ResourceNotFoundException("Hallazgo no encontrado"));

        if (finding.getVoidedAt() != null) {
            throw new BusinessException("El hallazgo ya está anulado");
        }

        clinicalDirectory.requireModifiableEncounter(finding.getEncounterId());

        finding.setVoidedAt(clinicClock.now());
        findingRepository.save(finding);
    }

    @Transactional(readOnly = true)
    public OdontogramResponse getPatientOdontogram(UUID patientId) {
        patientDirectory.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado"));

        List<DentalFinding> findings = findingRepository.findValidFindingsByPatientIdOrderByCreatedAt(patientId);
        CatalogMaps catalog = loadCatalogMaps();

        List<OdontogramFinding> allFindings = findings.stream()
                .map(f -> toOdontogramFinding(f, catalog))
                .toList();

        List<OdontogramFinding> activeFindings = OdontogramStateCalculator.calculateState(allFindings);

        DentalIndexCalculator.Result indexResult = DentalIndexCalculator.calculate(activeFindings);

        List<FindingResponse> findingsDto = activeFindings.stream()
                .map(domain -> toFindingResponseFromDomain(domain, catalog))
                .toList();

        DentalIndexResponse indexResponse = new DentalIndexResponse(
                indexResult.indices().cpoC(), indexResult.indices().cpoP(), indexResult.indices().cpoO(), indexResult.indices().cpoTotal(),
                indexResult.indices().ceoC(), indexResult.indices().ceoE(), indexResult.indices().ceoO(), indexResult.indices().ceoTotal()
        );

        return new OdontogramResponse(patientId, findingsDto, indexResponse, indexResult.cariesSurfaces());
    }

    private List<FindingResponse> mapToResponses(List<DentalFinding> findings) {
        if (findings.isEmpty()) return List.of();
        CatalogMaps catalog = loadCatalogMaps();

        return findings.stream()
                .map(f -> toOdontogramFinding(f, catalog))
                .map(domain -> toFindingResponseFromDomain(domain, catalog))
                .toList();
    }

    private record CatalogMaps(
            Map<Short, Tooth> teeth,
            Map<String, ToothSurface> surfaces,
            Map<Long, DentalCondition> conditions,
            Map<Long, List<DentalConditionSymbol>> symbols
    ) {}

    private CatalogMaps loadCatalogMaps() {
        return new CatalogMaps(
                toothRepository.findAll().stream().collect(Collectors.toMap(Tooth::getToothCode, t -> t)),
                toothSurfaceRepository.findAll().stream().collect(Collectors.toMap(ToothSurface::getSurfaceCode, s -> s)),
                conditionRepository.findAll().stream().collect(Collectors.toMap(DentalCondition::getDentalConditionId, c -> c)),
                symbolRepository.findAll().stream().collect(Collectors.groupingBy(DentalConditionSymbol::getDentalConditionId))
        );
    }

    private OdontogramFinding toOdontogramFinding(DentalFinding finding, CatalogMaps catalog) {
        Tooth tooth = catalog.teeth().get(finding.getToothCode());
        DentalCondition condition = catalog.conditions().get(finding.getDentalConditionId());
        ToothSurface surface = finding.getSurfaceCode() != null ? catalog.surfaces().get(finding.getSurfaceCode()) : null;

        return new OdontogramFinding(
                finding.getDentalFindingId(),
                finding.getToothCode(),
                finding.getEndToothCode(),
                finding.getSurfaceCode(),
                finding.getDentalConditionId(),
                condition.getCode(),
                condition.getScope(),
                condition.getCpoComponent(),
                condition.getCeoComponent(),
                tooth.getDentition(),
                tooth.getArch(),
                tooth.getToothType(),
                finding.getStatus(),
                finding.getGrade(),
                finding.getNotes(),
                finding.getCreatedAt(),
                surface != null ? surface.getName() : null
        );
    }

    private FindingResponse toFindingResponseFromDomain(OdontogramFinding domain, CatalogMaps catalog) {
        String surfaceLabel = SurfaceLabelResolver.resolveLabel(domain.surfaceCode(), domain.arch(), domain.toothType(), domain.surfaceName());

        List<DentalConditionSymbol> symbols = catalog.symbols().getOrDefault(domain.conditionId(), List.of());
        DentalConditionSymbol symbolEntity = symbols.stream()
                .filter(s -> s.getStatus() == domain.status())
                .findFirst()
                .orElse(null);
                
        SymbolResponse symbolResponse = null;
        if (symbolEntity != null) {
            symbolResponse = new SymbolResponse(
                    symbolEntity.getDentalConditionSymbolId(), symbolEntity.getStatus(),
                    symbolEntity.getShape(), symbolEntity.getColor(), symbolEntity.getSecondaryColor(),
                    symbolEntity.getFilled(), symbolEntity.getLineStyle()
            );
        }

        return new FindingResponse(
                domain.id(),
                domain.toothCode(),
                domain.endToothCode(),
                domain.surfaceCode(),
                surfaceLabel,
                domain.conditionId(),
                domain.status(),
                domain.grade(),
                domain.notes(),
                domain.createdAt(),
                symbolResponse
        );
    }
}
