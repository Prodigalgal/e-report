package com.pro.ereportbacken.model.dto;

public record ReverseGeocodeCandidateDTO(
        String name,
        String category,
        int distanceMeters
) {
}
