package com.bellidental.odontogram.application;

import com.bellidental.odontogram.FindingSummary;
import com.bellidental.odontogram.OdontogramDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OdontogramDirectoryImpl implements OdontogramDirectory {

    private final OdontogramService odontogramService;

    @Override
    public Optional<FindingSummary> findFinding(UUID findingId) {
        return odontogramService.findFinding(findingId);
    }

    @Override
    public FindingSummary recordDoneFor(UUID findingId, UUID encounterId) {
        return odontogramService.recordDoneFor(findingId, encounterId);
    }
}
