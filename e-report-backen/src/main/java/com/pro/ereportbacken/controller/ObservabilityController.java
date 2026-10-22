package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.ClientObservationBatchRequestDTO;
import com.pro.ereportbacken.model.dto.ClientObservationIngestResponseDTO;
import com.pro.ereportbacken.model.dto.ObservationPolicyDTO;
import com.pro.ereportbacken.service.ObservationIngestionService;
import com.pro.ereportbacken.service.ObservationRateLimiter;
import com.pro.ereportbacken.service.ObservationRequestIdentityResolver;
import com.pro.ereportbacken.service.ObservationSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/observability")
public class ObservabilityController {

    private final ObservationIngestionService ingestionService;
    private final ObservationSettingsService settingsService;
    private final ObservationRateLimiter rateLimiter;
    private final ObservationRequestIdentityResolver identityResolver;

    public ObservabilityController(ObservationIngestionService ingestionService,
                                   ObservationSettingsService settingsService,
                                   ObservationRateLimiter rateLimiter,
                                   ObservationRequestIdentityResolver identityResolver) {
        this.ingestionService = ingestionService;
        this.settingsService = settingsService;
        this.rateLimiter = rateLimiter;
        this.identityResolver = identityResolver;
    }

    @GetMapping("/policy")
    public ResponseEntity<ObservationPolicyDTO> policy() {
        return ResponseEntity.ok(settingsService.currentPolicyDto());
    }

    @PostMapping("/events/batch")
    public ResponseEntity<ClientObservationIngestResponseDTO> ingest(
            @Valid @RequestBody ClientObservationBatchRequestDTO request,
            HttpServletRequest servletRequest) {
        rateLimiter.check(servletRequest, request.events().size(), identityResolver.resolve().authenticated());
        ClientObservationIngestResponseDTO response = ingestionService.ingest(
                request,
                servletRequest.getHeader("User-Agent")
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
