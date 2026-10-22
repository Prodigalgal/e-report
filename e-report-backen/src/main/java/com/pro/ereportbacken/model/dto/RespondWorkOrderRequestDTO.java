package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.config.WorkOrderAttachmentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class RespondWorkOrderRequestDTO {
    @NotNull(message = "工单版本不能为空")
    private Long expectedVersion;

    @NotBlank(message = "处理情况不能为空")
    private String resolutionDetails;

    @NotBlank(message = "处理方式不能为空")
    private String resolutionMethod;

    private Boolean replacementCompleted;

    @Size(max = 2000, message = "换箱结果不能超过2000字符")
    private String replacementResult;

    @Valid
    private List<MaterialUsageDTO> materialUsages = new ArrayList<>();

    @Size(max = WorkOrderAttachmentLimits.MAX_FILE_COUNT,
            message = WorkOrderAttachmentLimits.RESPONSE_COUNT_LIMIT_MESSAGE)
    private List<@NotNull(message = "回单附件标识不能为空") UUID> uploadedAttachmentIds = new ArrayList<>();
}
