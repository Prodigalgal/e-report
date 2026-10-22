package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.ReverseGeocodeResponseDTO;
import com.pro.ereportbacken.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/location")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/reverse-geocode")
    public ResponseEntity<ReverseGeocodeResponseDTO> reverseGeocode(@RequestParam String gpsCoordinates) {
        return ResponseEntity.ok(locationService.reverseGeocode(gpsCoordinates));
    }
}
