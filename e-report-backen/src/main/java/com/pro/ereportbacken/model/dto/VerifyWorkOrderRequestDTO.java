package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.VerificationDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyWorkOrderRequestDTO {
    @NotNull(message = "回单记录不能为空")
    private UUID responseId;

    @NotNull(message = "验收结论不能为空")
    private VerificationDecision decision;

    @NotBlank(message = "验收意见不能为空")
    @Size(max = 2000, message = "验收意见不能超过2000字符")
    private String comment;

    private UUID reassignToMembershipId;
    private UUID responsibleOrgId;
    private UUID responsibleDepartmentId;

    @NotNull(message = "工单版本不能为空")
    private Long expectedVersion;
}

