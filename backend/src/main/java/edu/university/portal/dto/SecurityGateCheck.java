package edu.university.portal.dto;

import edu.university.portal.model.SecurityGateStatus;

public record SecurityGateCheck(
        String gate,
        String label,
        SecurityGateStatus status,
        String message
) {
}
