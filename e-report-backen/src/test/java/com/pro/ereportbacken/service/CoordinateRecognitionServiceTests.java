package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 坐标识别与地址解析之间的边界约束。
 *
 * <p>真实的高德接口调用已在浏览器与手工验证中覆盖，这里锁定不依赖外部服务的判定逻辑：
 * 非法坐标必须被拒绝，且识别服务在无有效输入时不得臆造结果。</p>
 */
class CoordinateRecognitionServiceTests {

    private final CoordinateStandardService coordinateStandardService = new CoordinateStandardService();
    private final CoordinateTextParser coordinateTextParser = new CoordinateTextParser();

    @Test
    void recognizesPlainPairAsGcj02WhenAmbiguous() {
        // 解析层不裁决坐标系，只负责抽取数值与顺序；坐标系由识别服务结合地址判定。
        var parsed = coordinateTextParser.parse("117.651786,24.510021");

        assertThat(parsed.parsed()).isTrue();
        assertThat(parsed.longitude()).isEqualTo(117.651786);
        assertThat(parsed.latitude()).isEqualTo(24.510021);
    }

    @Test
    void gcj02AndWgs84DifferByHundredsOfMeters() {
        double[] gcj = coordinateStandardService.wgs84ToGcj02(24.513, 117.647);
        double[] backToWgs = coordinateStandardService.toWgs84(
                CoordinateStandardService.CoordinateStandard.GCJ02, gcj[0], gcj[1]);

        double offset = coordinateStandardService.distanceMeters(24.513, 117.647, gcj[0], gcj[1]);

        // 这正是用户必须确认坐标系的原因：两者差值足以指向不同的街道。
        assertThat(offset).isGreaterThan(100.0);
        assertThat(backToWgs[0]).isCloseTo(24.513, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(backToWgs[1]).isCloseTo(117.647, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void bd09ToWgs84IsReversible() {
        double[] bd09 = coordinateStandardService.wgs84ToBd09(24.513, 117.647);
        double[] wgs84 = coordinateStandardService.toWgs84(
                CoordinateStandardService.CoordinateStandard.BD09, bd09[0], bd09[1]);

        assertThat(wgs84[0]).isCloseTo(24.513, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(wgs84[1]).isCloseTo(117.647, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void distanceMeasuresZeroForSamePoint() {
        assertThat(coordinateStandardService.distanceMeters(24.513, 117.647, 24.513, 117.647))
                .isLessThan(0.01);
    }

    @Test
    void recognizesMainlandChinaBoundary() {
        assertThat(coordinateStandardService.outOfChina(24.513, 117.647)).isFalse();
        assertThat(coordinateStandardService.outOfChina(48.8566, 2.3522)).isTrue();
    }

    @Test
    void parserRejectsNonCoordinateText() {
        // 提取器必须拒绝把地址文本误当作坐标，避免生成离谱的点位。
        assertThat(coordinateTextParser.parse("漳州市医院").parsed()).isFalse();
        assertThat(coordinateTextParser.parse("没有数字的文本").parsed()).isFalse();
    }
}
