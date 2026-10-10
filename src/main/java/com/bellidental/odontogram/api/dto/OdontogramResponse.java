package com.bellidental.odontogram.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OdontogramResponse(
        UUID patientId,
        List<FindingResponse> findings,
        DentalIndexResponse indices,
        Map<Short, Integer> cariesSurfaces
) {
}
