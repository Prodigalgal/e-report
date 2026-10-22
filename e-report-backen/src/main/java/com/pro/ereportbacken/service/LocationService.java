package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.ReverseGeocodeCandidateDTO;
import com.pro.ereportbacken.model.dto.ReverseGeocodeResolutionStatus;
import com.pro.ereportbacken.model.dto.ReverseGeocodeResponseDTO;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Log4j2
public class LocationService {

    private static final String AMAP_REVERSE_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/regeo";
    private static final String AMAP_AROUND_SEARCH_URL = "https://restapi.amap.com/v5/place/around";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int ADMINISTRATIVE_POI_SEARCH_RADIUS_METERS = 3000;
    private static final String ADMINISTRATIVE_POI_TYPE_CODES = "120304|190108|190109";
    private static final Pattern ADMINISTRATIVE_LOCALITY_NAME = Pattern.compile(
            ".*(村|社区|居委会|村委会|居民委员会|村民委员会|村民小组)$"
    );

    private final MapKeyPoolService mapKeyPoolService;
    private final MapService mapService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LocationService(MapKeyPoolService mapKeyPoolService, MapService mapService) {
        this.mapKeyPoolService = mapKeyPoolService;
        this.mapService = mapService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    public ReverseGeocodeResponseDTO reverseGeocode(String gpsCoordinates) {
        GeoPoint point = parseGeoPoint(gpsCoordinates);
        String amapKey = mapKeyPoolService.nextAmapKey();
        if (!StringUtils.hasText(amapKey)) {
            throw new IllegalStateException("高德Web服务API Key池未配置。");
        }

        String normalizedGps = point.toDisplayValue();
        MapService.Point amapPoint = mapService.wgs84ToGcj02(point.latitude(), point.longitude());
        URI uri = buildReverseGeocodeUri(amapPoint, amapKey);
        try {
            ParsedReverseGeocode parsed = parseReverseGeocode(sendGet(uri, "高德逆地理接口"));
            ReverseGeocodeResponseDTO reverseResult = resolveResponse(normalizedGps, parsed, List.of());
            if (reverseResult.resolutionStatus() == ReverseGeocodeResolutionStatus.CONFIRMED) {
                return reverseResult;
            }

            List<ReverseGeocodeCandidateDTO> aroundCandidates = searchAdministrativeCandidates(
                    amapPoint,
                    parsed.adcode(),
                    amapKey
            );
            return resolveResponse(normalizedGps, parsed, aroundCandidates);
        } catch (IOException | IllegalStateException e) {
            log.warn("高德逆地理接口请求失败: {}", e.getMessage());
            throw new IllegalStateException("高德逆地理接口请求失败，请稍后重试。", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("高德逆地理接口请求被中断。", e);
        }
    }

    private String sendGet(URI uri, String serviceName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(serviceName + "返回HTTP状态码: " + response.statusCode());
        }
        return response.body();
    }

    private URI buildReverseGeocodeUri(MapService.Point amapPoint, String amapKey) {
        String location = String.format(Locale.ROOT, "%.6f,%.6f", amapPoint.lon, amapPoint.lat);
        String query = "location=" + URLEncoder.encode(location, StandardCharsets.UTF_8)
                + "&key=" + URLEncoder.encode(amapKey, StandardCharsets.UTF_8)
                + "&radius=1000&extensions=all";
        return URI.create(AMAP_REVERSE_GEOCODE_URL + "?" + query);
    }

    private URI buildAroundSearchUri(MapService.Point amapPoint, String adcode, String amapKey) {
        String location = String.format(Locale.ROOT, "%.6f,%.6f", amapPoint.lon, amapPoint.lat);
        StringBuilder query = new StringBuilder()
                .append("location=").append(encode(location))
                .append("&key=").append(encode(amapKey))
                .append("&radius=").append(ADMINISTRATIVE_POI_SEARCH_RADIUS_METERS)
                .append("&types=").append(encode(ADMINISTRATIVE_POI_TYPE_CODES))
                .append("&sortrule=distance&page_size=20&show_fields=typecode");
        if (StringUtils.hasText(adcode)) {
            query.append("&region=").append(encode(adcode)).append("&city_limit=true");
        }
        return URI.create(AMAP_AROUND_SEARCH_URL + "?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    ReverseGeocodeResponseDTO parseAmapReverseGeocodeResponse(String gpsCoordinates, String responseBody) throws IOException {
        return resolveResponse(gpsCoordinates, parseReverseGeocode(responseBody), List.of());
    }

    private ParsedReverseGeocode parseReverseGeocode(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        if (!"1".equals(root.path("status").asText())) {
            String info = root.path("info").asText("未知错误");
            throw new IllegalStateException("高德逆地理接口返回失败: " + info);
        }

        JsonNode regeocode = root.path("regeocode");
        JsonNode component = regeocode.path("addressComponent");
        String township = textOf(component.path("township"));
        String neighborhood = textOf(component.path("neighborhood").path("name"));
        String confirmedNeighborhood = isAdministrativeLocalityName(neighborhood) ? neighborhood : "";
        return new ParsedReverseGeocode(
                township,
                textOf(regeocode.path("formatted_address")),
                textOf(component.path("adcode")),
                confirmedNeighborhood,
                candidatesFromPois(regeocode.path("pois"))
        );
    }

    private List<ReverseGeocodeCandidateDTO> searchAdministrativeCandidates(
            MapService.Point amapPoint,
            String adcode,
            String amapKey
    ) {
        try {
            String responseBody = sendGet(buildAroundSearchUri(amapPoint, adcode, amapKey), "高德行政地名搜索接口");
            JsonNode root = objectMapper.readTree(responseBody);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("高德行政地名搜索失败: {}", root.path("info").asText("未知错误"));
                return List.of();
            }
            return candidatesFromPois(root.path("pois"));
        } catch (IOException | IllegalStateException e) {
            log.warn("高德行政地名搜索请求失败，将保留逆地址基础结果: {}", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("高德行政地名搜索请求被中断，将保留逆地址基础结果");
            return List.of();
        }
    }

    private List<ReverseGeocodeCandidateDTO> candidatesFromPois(JsonNode pois) {
        if (!pois.isArray()) {
            return List.of();
        }
        List<ReverseGeocodeCandidateDTO> candidates = new ArrayList<>();
        for (JsonNode poi : pois) {
            String name = textOf(poi.path("name"));
            String type = textOf(poi.path("type"));
            String typeCode = textOf(poi.path("typecode"));
            if (isAdministrativePoi(typeCode, type) && isAdministrativeLocalityName(name)) {
                candidates.add(new ReverseGeocodeCandidateDTO(
                        name,
                        administrativeCategory(typeCode, type, name),
                        distanceOf(poi.path("distance"))
                ));
            }
        }
        return candidates;
    }

    private boolean isAdministrativePoi(String typeCode, String poiType) {
        return "120304".equals(typeCode)
                || "190108".equals(typeCode)
                || "190109".equals(typeCode)
                || poiType.contains("村庄级地名")
                || poiType.contains("村组级地名")
                || poiType.contains("社区中心");
    }

    private String administrativeCategory(String typeCode, String poiType, String name) {
        if ("120304".equals(typeCode) || poiType.contains("社区中心") || name.endsWith("社区")) {
            return "社区";
        }
        if ("190109".equals(typeCode) || poiType.contains("村组级地名") || name.endsWith("村民小组")) {
            return "村民小组";
        }
        return "行政村";
    }

    private int distanceOf(JsonNode node) {
        String value = textOf(node);
        if (!StringUtils.hasText(value)) {
            return -1;
        }
        try {
            return Math.max(0, (int) Math.round(Double.parseDouble(value)));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private ReverseGeocodeResponseDTO resolveResponse(
            String gpsCoordinates,
            ParsedReverseGeocode parsed,
            List<ReverseGeocodeCandidateDTO> additionalCandidates
    ) {
        List<ReverseGeocodeCandidateDTO> candidates = mergeCandidates(parsed.embeddedCandidates(), additionalCandidates);
        if (StringUtils.hasText(parsed.confirmedNeighborhood())) {
            candidates = mergeCandidates(
                    List.of(new ReverseGeocodeCandidateDTO(parsed.confirmedNeighborhood(), localityCategory(parsed.confirmedNeighborhood()), 0)),
                    candidates
            );
            return new ReverseGeocodeResponseDTO(
                    gpsCoordinates,
                    parsed.township(),
                    parsed.confirmedNeighborhood(),
                    parsed.formattedAddress(),
                    ReverseGeocodeResolutionStatus.CONFIRMED,
                    candidates
            );
        }

        String addressConfirmedCandidate = candidates.stream()
                .map(ReverseGeocodeCandidateDTO::name)
                .filter(parsed.formattedAddress()::contains)
                .findFirst()
                .orElse("");
        ReverseGeocodeResolutionStatus status = StringUtils.hasText(addressConfirmedCandidate)
                ? ReverseGeocodeResolutionStatus.CONFIRMED
                : candidates.isEmpty()
                ? ReverseGeocodeResolutionStatus.UNRESOLVED
                : ReverseGeocodeResolutionStatus.NEEDS_CONFIRMATION;
        return new ReverseGeocodeResponseDTO(
                gpsCoordinates,
                parsed.township(),
                addressConfirmedCandidate,
                parsed.formattedAddress(),
                status,
                candidates
        );
    }

    private List<ReverseGeocodeCandidateDTO> mergeCandidates(
            List<ReverseGeocodeCandidateDTO> embeddedCandidates,
            List<ReverseGeocodeCandidateDTO> additionalCandidates
    ) {
        Map<String, ReverseGeocodeCandidateDTO> byName = new LinkedHashMap<>();
        embeddedCandidates.forEach(candidate -> byName.merge(candidate.name(), candidate, this::preferCloserCandidate));
        additionalCandidates.forEach(candidate -> byName.merge(candidate.name(), candidate, this::preferCloserCandidate));
        return byName.values().stream()
                .sorted(Comparator.comparingInt(this::sortableDistance).thenComparing(ReverseGeocodeCandidateDTO::name))
                .toList();
    }

    private ReverseGeocodeCandidateDTO preferCloserCandidate(
            ReverseGeocodeCandidateDTO first,
            ReverseGeocodeCandidateDTO second
    ) {
        return sortableDistance(first) <= sortableDistance(second) ? first : second;
    }

    private int sortableDistance(ReverseGeocodeCandidateDTO candidate) {
        return candidate.distanceMeters() < 0 ? Integer.MAX_VALUE : candidate.distanceMeters();
    }

    private String localityCategory(String name) {
        if (name.endsWith("社区") || name.endsWith("居委会") || name.endsWith("居民委员会")) {
            return "社区";
        }
        if (name.endsWith("村民小组")) {
            return "村民小组";
        }
        return "行政村";
    }

    private boolean isAdministrativeLocalityName(String name) {
        return StringUtils.hasText(name) && ADMINISTRATIVE_LOCALITY_NAME.matcher(name).matches();
    }

    private GeoPoint parseGeoPoint(String gpsCoordinates) {
        if (!StringUtils.hasText(gpsCoordinates)) {
            throw new IllegalArgumentException("定位坐标不能为空。");
        }

        String[] parts = gpsCoordinates.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("定位坐标格式必须为：经度,纬度。");
        }

        double first = parseCoordinate(parts[0], "经度");
        double second = parseCoordinate(parts[1], "纬度");
        double longitude = first;
        double latitude = second;

        if (Math.abs(first) <= 90 && Math.abs(second) > 90) {
            longitude = second;
            latitude = first;
        }

        if (Math.abs(longitude) > 180 || Math.abs(latitude) > 90) {
            throw new IllegalArgumentException("定位坐标超出合法范围，请使用经度,纬度格式。");
        }
        return new GeoPoint(longitude, latitude);
    }

    private double parseCoordinate(String rawValue, String label) {
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "不是合法数字。", e);
        }
    }

    private String textOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isValueNode()) {
            return "";
        }
        String value = node.asText("");
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return "[]".equals(trimmed) || "{}".equals(trimmed) ? "" : trimmed;
    }

    private record GeoPoint(double longitude, double latitude) {
        private String toDisplayValue() {
            return String.format(Locale.ROOT, "%.6f,%.6f", longitude, latitude);
        }
    }

    private record ParsedReverseGeocode(
            String township,
            String formattedAddress,
            String adcode,
            String confirmedNeighborhood,
            List<ReverseGeocodeCandidateDTO> embeddedCandidates
    ) {
    }

}
