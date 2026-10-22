package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.WorkOrderTransferCreateRequestDTO;
import com.pro.ereportbacken.model.dto.WorkOrderTransferDTO;
import com.pro.ereportbacken.model.dto.WorkOrderTransferDecisionRequestDTO;
import com.pro.ereportbacken.model.dto.WorkOrderTransferTargetDTO;
import com.pro.ereportbacken.service.IdempotencyService;
import com.pro.ereportbacken.service.WorkOrderOrganizationTransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/work-orders")
public class WorkOrderOrganizationTransferController {

    private final WorkOrderOrganizationTransferService transferService;
    private final IdempotencyService idempotencyService;

    public WorkOrderOrganizationTransferController(WorkOrderOrganizationTransferService transferService,
                                                   IdempotencyService idempotencyService) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/organization-transfer-targets")
    public ResponseEntity<List<WorkOrderTransferTargetDTO>> targets() {
        return ResponseEntity.ok(transferService.targets());
    }

    @GetMapping("/{workOrderId}/organization-transfers")
    public ResponseEntity<List<WorkOrderTransferDTO>> history(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(transferService.history(workOrderId));
    }

    @PostMapping("/{workOrderId}/organization-transfers")
    public ResponseEntity<WorkOrderTransferDTO> request(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkOrderTransferCreateRequestDTO request) {
        WorkOrderTransferDTO response = idempotencyService.execute(
                idempotencyKey,
                "POST:/work-orders/" + workOrderId + "/organization-transfers",
                request,
                WorkOrderTransferDTO.class,
                () -> transferService.request(workOrderId, request));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/{workOrderId}/organization-transfers/{transferId}/accept")
    public ResponseEntity<WorkOrderTransferDTO> accept(
            @PathVariable UUID workOrderId,
            @PathVariable UUID transferId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkOrderTransferDecisionRequestDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(
                idempotencyKey,
                "POST:/work-orders/" + workOrderId + "/organization-transfers/" + transferId + "/accept",
                request,
                WorkOrderTransferDTO.class,
                () -> transferService.accept(workOrderId, transferId, request)));
    }

    @PostMapping("/{workOrderId}/organization-transfers/{transferId}/reject")
    public ResponseEntity<WorkOrderTransferDTO> reject(
            @PathVariable UUID workOrderId,
            @PathVariable UUID transferId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkOrderTransferDecisionRequestDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(
                idempotencyKey,
                "POST:/work-orders/" + workOrderId + "/organization-transfers/" + transferId + "/reject",
                request,
                WorkOrderTransferDTO.class,
                () -> transferService.reject(workOrderId, transferId, request)));
    }

    @PostMapping("/{workOrderId}/organization-transfers/{transferId}/cancel")
    public ResponseEntity<WorkOrderTransferDTO> cancel(
            @PathVariable UUID workOrderId,
            @PathVariable UUID transferId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkOrderTransferDecisionRequestDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(
                idempotencyKey,
                "POST:/work-orders/" + workOrderId + "/organization-transfers/" + transferId + "/cancel",
                request,
                WorkOrderTransferDTO.class,
                () -> transferService.cancel(workOrderId, transferId, request)));
    }
}
