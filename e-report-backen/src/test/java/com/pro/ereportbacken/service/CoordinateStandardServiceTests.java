package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinateStandardServiceTests {

    private final CoordinateStandardService service = new CoordinateStandardService();

    /** 漳州市区参考点，用于验证各标准之间的偏移量级。 */
    private static final double REFERENCE_LATITUDE = 24.513;
    private static final double REFERENCE_LONGITUDE = 117.647;

    @Test
    void wgs84ToGcj02OffsetsWithinExpectedRange() {
        double[] gcj = service.wgs84ToGcj02(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);

        double offsetMeters = service.distanceMeters(
                REFERENCE_LATITUDE, REFERENCE_LONGITUDE, gcj[0], gcj[1]);

        // GCJ-02 加密偏移在中国大陆通常为数百米量级，方向随经纬度变化，不做方向断言。
        assertThat(offsetMeters).isGreaterThan(100.0);
        assertThat(offsetMeters).isLessThan(900.0);
    }

    /**
     * 与高德官方 JS API 实测值比对：WGS84 117.647,24.513 经官方 convertFrom 得到 117.651786,24.510021。
     * 该用例锁定本地算法与官方结果一致，防止转换实现被无意改动。
     */
    @Test
    void wgs84ToGcj02MatchesAmapOfficialConversion() {
        double[] gcj = service.wgs84ToGcj02(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);

        assertThat(gcj[1]).isCloseTo(117.651786, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(gcj[0]).isCloseTo(24.510021, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void gcj02ToWgs84RoundTripsBackToOriginal() {
        double[] gcj = service.wgs84ToGcj02(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);
        double[] wgs84 = service.gcj02ToWgs84(gcj[0], gcj[1]);

        assertThat(wgs84[0]).isCloseTo(REFERENCE_LATITUDE, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(wgs84[1]).isCloseTo(REFERENCE_LONGITUDE, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void bd09RoundTripsThroughGcj02() {
        double[] bd09 = service.gcj02ToBd09(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);
        double[] backToGcj = service.bd09ToGcj02(bd09[0], bd09[1]);

        assertThat(backToGcj[0]).isCloseTo(REFERENCE_LATITUDE, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(backToGcj[1]).isCloseTo(REFERENCE_LONGITUDE, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void bd09AndGcj02AreWithinHundredsOfMeters() {
        double[] bd09 = service.gcj02ToBd09(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);

        double offsetMeters = service.distanceMeters(
                REFERENCE_LATITUDE, REFERENCE_LONGITUDE, bd09[0], bd09[1]);

        // BD-09 在 GCJ-02 基础上再偏移，量级同样在数百米内。
        assertThat(offsetMeters).isGreaterThan(50.0);
        assertThat(offsetMeters).isLessThan(1500.0);
    }

    @Test
    void allThreeStandardsConvertToConsistentWgs84() {
        // 同一真实地点的三种标准表示，转换到 WGS84 后应落在彼此数百米内。
        double[] fromWgs = service.toWgs84(
                CoordinateStandardService.CoordinateStandard.WGS84, REFERENCE_LATITUDE, REFERENCE_LONGITUDE);

        double[] gcj = service.wgs84ToGcj02(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);
        double[] fromGcj = service.toWgs84(
                CoordinateStandardService.CoordinateStandard.GCJ02, gcj[0], gcj[1]);

        double[] bd09 = service.gcj02ToBd09(gcj[0], gcj[1]);
        double[] fromBd = service.toWgs84(
                CoordinateStandardService.CoordinateStandard.BD09, bd09[0], bd09[1]);

        assertThat(fromGcj[0]).isCloseTo(fromWgs[0], org.assertj.core.data.Offset.offset(1e-5));
        assertThat(fromGcj[1]).isCloseTo(fromWgs[1], org.assertj.core.data.Offset.offset(1e-5));
        // BD-09 经迭代逆变换后回到 WGS84，残差在亚米级（约 1e-6 度）以内。
        assertThat(fromBd[0]).isCloseTo(fromWgs[0], org.assertj.core.data.Offset.offset(1e-5));
        assertThat(fromBd[1]).isCloseTo(fromWgs[1], org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void coordinatesOutsideChinaAreNotShifted() {
        // 境外坐标不做加密偏移，三种标准等价。
        double[] result = service.wgs84ToGcj02(48.8566, 2.3522);

        assertThat(result[0]).isEqualTo(48.8566);
        assertThat(result[1]).isEqualTo(2.3522);
        assertThat(service.outOfChina(48.8566, 2.3522)).isTrue();
    }

    @Test
    void standardResolvesFromCodeCaseInsensitively() {
        assertThat(CoordinateStandardService.CoordinateStandard.fromCode("WGS84"))
                .isEqualTo(CoordinateStandardService.CoordinateStandard.WGS84);
        assertThat(CoordinateStandardService.CoordinateStandard.fromCode("gcj02"))
                .isEqualTo(CoordinateStandardService.CoordinateStandard.GCJ02);
        assertThat(CoordinateStandardService.CoordinateStandard.fromCode("BD09"))
                .isEqualTo(CoordinateStandardService.CoordinateStandard.BD09);
        assertThat(CoordinateStandardService.CoordinateStandard.fromCode("unknown")).isNull();
        assertThat(CoordinateStandardService.CoordinateStandard.fromCode(null)).isNull();
    }
}
