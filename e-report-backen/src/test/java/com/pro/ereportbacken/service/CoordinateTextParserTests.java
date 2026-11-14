package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.CoordinateParseResultDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinateTextParserTests {

    private final CoordinateTextParser parser = new CoordinateTextParser();

    @Test
    void parsesPlainLongitudeLatitudePair() {
        CoordinateParseResultDTO result = parser.parse("117.647,24.513");

        assertThat(result.parsed()).isTrue();
        assertThat(result.longitude()).isEqualTo(117.647);
        assertThat(result.latitude()).isEqualTo(24.513);
        assertThat(result.reordered()).isFalse();
        assertThat(result.detectedOrder()).isEqualTo("经度,纬度");
    }

    @Test
    void parsesPairSeparatedByFullWidthComma() {
        CoordinateParseResultDTO result = parser.parse("117.647，24.513");

        assertThat(result.parsed()).isTrue();
        assertThat(result.longitude()).isEqualTo(117.647);
        assertThat(result.latitude()).isEqualTo(24.513);
    }

    @Test
    void parsesPairSeparatedBySpaceOrNewline() {
        assertThat(parser.parse("117.647 24.513").parsed()).isTrue();
        assertThat(parser.parse("117.647\n24.513").parsed()).isTrue();
    }

    @Test
    void treatsAmbiguousPairAsLongitudeLatitudeAndFlagsForReview() {
        // 两项都落在合法经纬度范围内且两种解释都成立时，按最常见约定解析并提示用户核对。
        // 24.513 与 30.0 同时是合法经度与合法纬度。
        CoordinateParseResultDTO result = parser.parse("24.513,30.0");

        assertThat(result.parsed()).isTrue();
        assertThat(result.longitude()).isEqualTo(24.513);
        assertThat(result.latitude()).isEqualTo(30.0);
        assertThat(result.reordered()).isFalse();
        assertThat(result.notes()).anyMatch(note -> note.contains("请核对"));
    }

    @Test
    void reordersWhenFirstValueCannotBeLatitude() {
        // 117.647 超出纬度上限，只能是经度；第二项 24.513 两者皆可 → 判定为「纬度,经度」并换序。
        CoordinateParseResultDTO result = parser.parse("24.513,117.647");

        assertThat(result.parsed()).isTrue();
        assertThat(result.longitude()).isEqualTo(117.647);
        assertThat(result.latitude()).isEqualTo(24.513);
        assertThat(result.reordered()).isTrue();
        assertThat(result.detectedOrder()).isEqualTo("纬度,经度");
    }

    @Test
    void rejectsPairsWithNoValidInterpretation() {
        // 200 同时越过经度与纬度上限。
        assertThat(parser.parse("200.0,24.513").parsed()).isFalse();
        // 两项都超出纬度上限，且 200 也超出经度上限。
        assertThat(parser.parse("200.0,300.0").parsed()).isFalse();
    }

    @Test
    void parsesLabelledCoordinatesRegardlessOfOrder() {
        CoordinateParseResultDTO result = parser.parse("lat: 24.513, lng: 117.647");

        assertThat(result.parsed()).isTrue();
        assertThat(result.longitude()).isEqualTo(117.647);
        assertThat(result.latitude()).isEqualTo(24.513);
        assertThat(result.detectedOrder()).isEqualTo("经度,纬度");
    }

    @Test
    void parsesChineseLabels() {
        CoordinateParseResultDTO result = parser.parse("东经 117.647 北纬 24.513");

        assertThat(result.parsed()).isTrue();
        assertThat(result.longitude()).isEqualTo(117.647);
        assertThat(result.latitude()).isEqualTo(24.513);
    }

    @Test
    void parsesWktPoint() {
        CoordinateParseResultDTO result = parser.parse("POINT(117.647 24.513)");

        assertThat(result.parsed()).isTrue();
        assertThat(result.longitude()).isEqualTo(117.647);
        assertThat(result.latitude()).isEqualTo(24.513);
    }

    @Test
    void parsesDegreesMinutesSeconds() {
        CoordinateParseResultDTO result = parser.parse("117°38'49.2\"E 24°30'46.8\"N");

        assertThat(result.parsed()).isTrue();
        assertThat(result.longitude()).isCloseTo(117.647, org.assertj.core.data.Offset.offset(0.001));
        assertThat(result.latitude()).isCloseTo(24.513, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void rejectsTextWithoutCoordinates() {
        CoordinateParseResultDTO result = parser.parse("漳州市芗城区胜利西路120号");

        assertThat(result.parsed()).isFalse();
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThat(parser.parse("200.0,100.0").parsed()).isFalse();
    }

    @Test
    void rejectsBlankInput() {
        assertThat(parser.parse("").parsed()).isFalse();
        assertThat(parser.parse("   ").parsed()).isFalse();
        assertThat(parser.parse(null).parsed()).isFalse();
    }

    @Test
    void preservesNegativeCoordinates() {
        CoordinateParseResultDTO result = parser.parse("-117.647,-24.513");

        assertThat(result.parsed()).isTrue();
        assertThat(result.longitude()).isEqualTo(-117.647);
        assertThat(result.latitude()).isEqualTo(-24.513);
    }
}
