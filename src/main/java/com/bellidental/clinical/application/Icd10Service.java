package com.bellidental.clinical.application;

import com.bellidental.clinical.api.dto.Icd10Response;
import com.bellidental.clinical.domain.Icd10Query;
import com.bellidental.persistence.entity.Icd10Code;
import com.bellidental.persistence.repository.Icd10CodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class Icd10Service {

    private final Icd10CodeRepository icd10CodeRepository;

    @Transactional(readOnly = true)
    public List<Icd10Response> search(String rawQuery) {
        Icd10Query query = Icd10Query.parse(rawQuery);
        List<Icd10Code> codes = query.codeSearch()
                ? icd10CodeRepository.findActiveByCodePrefix(query.value())
                : icd10CodeRepository.findActiveByDescription(query.value());
        return codes.stream().map(this::toResponse).toList();
    }

    private Icd10Response toResponse(Icd10Code code) {
        return new Icd10Response(code.getId(), code.getCode(), code.getDescription(), code.getCategory());
    }
}
