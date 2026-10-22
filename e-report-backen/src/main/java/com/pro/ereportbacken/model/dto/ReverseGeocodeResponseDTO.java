package com.pro.ereportbacken.model.dto;

import java.util.List;

public record ReverseGeocodeResponseDTO(
        String gpsCoordinates,
        String township,
        String villageOrStreet,
        String formattedAddress,
        ReverseGeocodeResolutionStatus resolutionStatus,
        List<ReverseGeocodeCandidateDTO> candidates
) {
}
