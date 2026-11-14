package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.CoordinateRecognitionResponseDTO;
import com.pro.ereportbacken.model.dto.ReverseGeocodeResponseDTO;
import com.pro.ereportbacken.model.dto.ReverseGeocodeResolutionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 坐标识别响应的字段装配校验。
 *
 * <p>这里刻意使用中国大陆范围之外的坐标：该路径不触发逆地理编码，
 * 因此可以在没有外部地图服务的情况下验证 DTO 各字段是否装配到正确的经纬度上。
 * 经纬度一旦互换，界面会把标记画到非法位置，而契约字符串仍然正确，属于最难肉眼发现的错误。</p>
 */
class CoordinateRecognitionAssemblyTests {

    /** 巴黎：纬度 48.8566，经度 2.3522；两者量级不同，便于识破互换。 */
    private static final double PARIS_LATITUDE = 48.8566;
    private static final double PARIS_LONGITUDE = 2.3522;

    private final CoordinateStandardService coordinateStandardService = new CoordinateStandardService();

    private final CoordinateRecognitionService service = new CoordinateRecognitionService(
            coordinateStandardService,
            new CoordinateTextParser(),
            new NoopLocationService()
    );

    @Test
    void assemblesLongitudeAndLatitudeIntoTheDeclaredFields() {
        CoordinateRecognitionResponseDTO response = service.recognize(
                PARIS_LONGITUDE, PARIS_LATITUDE, null, List.of());

        assertThat(response.recognized()).isTrue();
        // 经度字段必须是经度，纬度字段必须是纬度。
        assertThat(response.wgs84Longitude()).isEqualTo(PARIS_LONGITUDE);
        assertThat(response.wgs84Latitude()).isEqualTo(PARIS_LATITUDE);
        assertThat(response.gcj02Longitude()).isEqualTo(PARIS_LONGITUDE);
        assertThat(response.gcj02Latitude()).isEqualTo(PARIS_LATITUDE);
        assertThat(response.sourceLongitude()).isEqualTo(PARIS_LONGITUDE);
        assertThat(response.sourceLatitude()).isEqualTo(PARIS_LATITUDE);
        // 契约字符串顺序为「经度,纬度」。
        assertThat(response.gpsCoordinates()).startsWith("2.352200,48.856600");
    }

    @Test
    void assemblesCandidateFieldsIntoTheDeclaredOrder() {
        CoordinateRecognitionResponseDTO response = service.recognize(
                PARIS_LONGITUDE, PARIS_LATITUDE, null, List.of());

        assertThat(response.candidates()).isNotEmpty();
        CoordinateRecognitionResponseDTO.CoordinateStandardCandidateDTO candidate = response.candidates().get(0);

        assertThat(candidate.sourceLongitude()).isEqualTo(PARIS_LONGITUDE);
        assertThat(candidate.sourceLatitude()).isEqualTo(PARIS_LATITUDE);
        assertThat(candidate.wgs84Longitude()).isEqualTo(PARIS_LONGITUDE);
        assertThat(candidate.wgs84Latitude()).isEqualTo(PARIS_LATITUDE);
        assertThat(candidate.gcj02Longitude()).isEqualTo(PARIS_LONGITUDE);
        assertThat(candidate.gcj02Latitude()).isEqualTo(PARIS_LATITUDE);
        assertThat(candidate.gpsCoordinates()).startsWith("2.352200,48.856600");
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        CoordinateRecognitionResponseDTO response = service.recognize(
                200.0, 24.513, null, List.of());

        assertThat(response.recognized()).isFalse();
        assertThat(response.message()).isNotBlank();
    }

    /** 境外路径不使用逆地理编码，避免测试依赖外部地图服务。 */
    private static final class NoopLocationService extends LocationService {
        NoopLocationService() {
            super(null, new MapService());
        }

        @Override
        public ReverseGeocodeResponseDTO reverseGeocode(String gpsCoordinates) {
            return new ReverseGeocodeResponseDTO(
                    gpsCoordinates, null, null, null,
                    ReverseGeocodeResolutionStatus.UNRESOLVED, List.of());
        }
    }
}
