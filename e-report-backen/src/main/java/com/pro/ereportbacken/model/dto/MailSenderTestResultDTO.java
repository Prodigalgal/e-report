package com.pro.ereportbacken.model.dto;

public record MailSenderTestResultDTO(
        boolean success,
        String message,
        String failureType
) {
}
