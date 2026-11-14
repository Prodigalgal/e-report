package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.CoordinateRecognitionResponseDTO;
import com.pro.ereportbacken.model.dto.GeocodeSearchResponseDTO;
import com.pro.ereportbacken.model.dto.ReverseGeocodeResponseDTO;
import com.pro.ereportbacken.model.dto.SessionSituationMapDTO;
import com.pro.ereportbacken.service.CoordinateRecognitionService;
import com.pro.ereportbacken.service.LocationService;
import com.pro.ereportbacken.service.SessionSituationMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/location")
public class LocationController {

    private final LocationService locationService;
    private final CoordinateRecognitionService coordinateRecognitionService;
    private final SessionSituationMapService sessionSituationMapService;

    public LocationController(LocationService locationService,
                              CoordinateRecognitionService coordinateRecognitionService,
                              SessionSituationMapService sessionSituationMapService) {
        this.locationService = locationService;
        this.coordinateRecognitionService = coordinateRecognitionService;
        this.sessionSituationMapService = sessionSituationMapService;
    }

    @GetMapping("/reverse-geocode")
    public ResponseEntity<ReverseGeocodeResponseDTO> reverseGeocode(@RequestParam String gpsCoordinates) {
        return ResponseEntity.ok(locationService.reverseGeocode(gpsCoordinates));
    }

    /**
     * 识别粘贴文本中的经纬度并统一转换到系统契约。用户无需知道自己手上是哪种标准的坐标。
     */
    @GetMapping("/coordinate/recognize")
    public ResponseEntity<CoordinateRecognitionResponseDTO> recognizeCoordinate(
            @RequestParam String text,
            @RequestParam(required = false) String standard) {
        return ResponseEntity.ok(coordinateRecognitionService.recognizeText(text, standard));
    }

    /**
     * 地址转坐标：用户在无法到达现场时按已知地点名称或地址定位，避免自行填写经纬度。
     */
    @GetMapping("/geocode")
    public ResponseEntity<GeocodeSearchResponseDTO> geocode(@RequestParam String keyword) {
        return ResponseEntity.ok(locationService.geocode(keyword));
    }

    /**
     * 坐标提取器所需的地图渲染凭据。
     *
     * <p>只返回前端地图密钥，不返回任何在线人员位置。报单人员不持有登录态地图查看权限，
     * 若复用管理员地图接口会因 403 导致整个提取器（含不依赖地图的粘贴识别）不可用。</p>
     */
    @GetMapping("/map-config")
    public ResponseEntity<SessionSituationMapDTO.MapClientConfigDTO> mapConfig() {
        return ResponseEntity.ok(sessionSituationMapService.mapClientConfig());
    }
}
