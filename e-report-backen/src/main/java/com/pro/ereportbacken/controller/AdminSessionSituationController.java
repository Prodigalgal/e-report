package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.SessionSituationMapDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.SessionSituationMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/session-situation")
public class AdminSessionSituationController {

    private final SessionSituationMapService sessionSituationMapService;

    public AdminSessionSituationController(SessionSituationMapService sessionSituationMapService) {
        this.sessionSituationMapService = sessionSituationMapService;
    }

    /**
     * 登录态态势地图：在线用户点位与授权范围内工单故障点，坐标均为 GCJ-02。
     */
    @GetMapping("/map")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_SESSION_LOCATION_VIEW + "')")
    public ResponseEntity<SessionSituationMapDTO> situationMap() {
        return ResponseEntity.ok(sessionSituationMapService.loadSituationMap());
    }
}
