package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DailyOutstandingReportScheduleUpdateRequestDTO(
        @NotBlank(message = "发送时间不能为空")
        @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d", message = "发送时间必须使用HH:mm格式")
        String sendTime
) {
}
