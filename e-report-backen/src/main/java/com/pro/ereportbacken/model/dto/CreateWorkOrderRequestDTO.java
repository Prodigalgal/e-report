package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.config.WorkOrderAttachmentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data // Lombok: generates getters, setters, toString, equals, hashCode
public class CreateWorkOrderRequestDTO {

    @NotBlank(message = "故障地点不能为空")
    @Size(max = 255, message = "故障地点长度不能超过255字符")
    private String location;

    @NotBlank(message = "定位坐标不能为空")
    @Size(max = 100, message = "定位坐标长度不能超过100字符")
    private String gpsCoordinates;

    @NotBlank(message = "故障原因不能为空")
    @Size(max = 5000, message = "故障原因长度不能超过5000字符")
    private String reason;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^\\d{7,15}$", message = "联系电话格式不正确") // Basic phone number pattern
    private String phone;

    @NotBlank(message = "联系人不能为空")
    @Size(max = 100, message = "联系人长度不能超过100字符")
    private String contactPerson;

    @NotNull(message = "工单类型不能为空")
    private WorkOrderType reportType;

    @NotNull(message = "工单优先级不能为空")
    private WorkOrderPriority priority;

    @Min(value = 1, message = "箱体数量不能小于1")
    @Max(value = 999, message = "箱体数量不能超过999")
    private Integer boxQuantity;

    @NotEmpty(message = "请至少上传一份现场附件")
    @Size(max = WorkOrderAttachmentLimits.MAX_FILE_COUNT,
            message = WorkOrderAttachmentLimits.REPORT_COUNT_LIMIT_MESSAGE)
    private List<@NotNull(message = "现场附件标识不能为空") UUID> uploadedAttachmentIds = new ArrayList<>();
}
