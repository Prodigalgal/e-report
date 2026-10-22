package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.UserTransferExecutionStatus;
import com.pro.ereportbacken.model.pojo.UserTransferValidationStatus;

import java.util.List;
import java.util.UUID;

public record UserTransferJobRowDTO(
        UUID id,
        String subjectKey,
        String subjectDisplay,
        String sheetName,
        int rowNumber,
        String commandType,
        UserTransferValidationStatus validationStatus,
        UserTransferExecutionStatus executionStatus,
        List<UserTransferFieldChangeDTO> fieldChanges,
        UserTransferAuthorizationDiffDTO authorizationDiff,
        List<String> validationErrors,
        String executionMessage,
        Long beforeVersion,
        Long afterVersion
) {
    public UserTransferJobRowDTO {
        fieldChanges = fieldChanges == null ? List.of() : List.copyOf(fieldChanges);
        authorizationDiff = authorizationDiff == null
                ? new UserTransferAuthorizationDiffDTO(null, List.of())
                : authorizationDiff;
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }
}
