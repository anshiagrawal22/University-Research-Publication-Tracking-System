package edu.university.portal.dto;

import edu.university.portal.model.SecurityGateStatus;

import java.time.Instant;
import java.util.List;

public record SecurityGateReport(
        SecurityGateStatus overallStatus,
        boolean publishAllowed,
        Instant checkedAt,
        String summary,
        List<SecurityGateCheck> checks
) {
}
