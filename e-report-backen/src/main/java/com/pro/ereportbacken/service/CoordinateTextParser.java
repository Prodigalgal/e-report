package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.CoordinateParseResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户粘贴的文本中提取经纬度。
 *
 * <p>用户拿到的坐标形态各异：从地图 App 复制的「经度,纬度」、带标签的「lat: 24.5 lng: 117.6」、
 * 微信定位的「纬度 经度」顺序、WKT 的「POINT(117.6 24.5)」、以及带后缀标识的百度/高德分享串。
 * 这里统一抽取数值并给出顺序推断，坐标系识别交由 {@link CoordinateRecognitionService} 依据
 * 地址一致性判定，避免仅凭数值范围误判。</p>
 */
@Service
public class CoordinateTextParser {

    /** 带明确标签的经度标识。 */
    private static final Pattern LONGITUDE_LABEL = Pattern.compile(
            "(?:lng|lon|long|longitude|经度|东经)\\s*[:：=]?\\s*(-?\\d{1,3}(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    /** 带明确标签的纬度标识。 */
    private static final Pattern LATITUDE_LABEL = Pattern.compile(
            "(?:lat|latitude|纬度|北纬)\\s*[:：=]?\\s*(-?\\d{1,3}(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    /** WKT 形式 POINT(lon lat)。 */
    private static final Pattern WKT_POINT = Pattern.compile(
            "POINT\\s*\\(\\s*(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    /** 无标签的裸数字对，允许逗号、空格、分号、顿号、换行分隔。 */
    private static final Pattern BARE_PAIR = Pattern.compile(
            "(-?\\d{1,3}(?:\\.\\d+)?)\\s*[,，;；、\\s]+\\s*(-?\\d{1,3}(?:\\.\\d+)?)");

    public CoordinateParseResultDTO parse(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return CoordinateParseResultDTO.unparsed("请粘贴或输入经纬度。");
        }
        String text = rawText.trim();

        if (CoordinateTextParserHelper.isDegreesMinutesSeconds(text)) {
            return parseDegreesMinutesSeconds(text);
        }

        Matcher longitudeLabel = LONGITUDE_LABEL.matcher(text);
        Matcher latitudeLabel = LATITUDE_LABEL.matcher(text);
        if (longitudeLabel.find() && latitudeLabel.find()) {
            Double longitude = toDouble(longitudeLabel.group(1));
            Double latitude = toDouble(latitudeLabel.group(1));
            return fromLabelled(longitude, latitude);
        }

        Matcher wkt = WKT_POINT.matcher(text);
        if (wkt.find()) {
            // WKT 约定顺序为 x y，即经度在前。
            Double longitude = toDouble(wkt.group(1));
            Double latitude = toDouble(wkt.group(2));
            return fromOrdered(longitude, latitude, "WKT POINT 顺序为经度,纬度");
        }

        Matcher barePair = BARE_PAIR.matcher(text);
        if (barePair.find()) {
            Double first = toDouble(barePair.group(1));
            Double second = toDouble(barePair.group(2));
            if (first == null || second == null) {
                return CoordinateParseResultDTO.unparsed("识别到的数值无法解析为有效坐标。");
            }
            // 裸数字对存在两种约定，交由范围推断；两者都合法时提示用户核对。
            return inferBareOrder(first, second);
        }

        return CoordinateParseResultDTO.unparsed("未能从文本中识别出经纬度，请检查格式，例如「117.647,24.513」。");
    }

    private CoordinateParseResultDTO fromLabelled(Double longitude, Double latitude) {
        if (!isValidLongitude(longitude) || !isValidLatitude(latitude)) {
            return CoordinateParseResultDTO.unparsed("标签中的数值超出经纬度合法范围。");
        }
        return CoordinateParseResultDTO.parsed(longitude, latitude, "经度,纬度", false,
                List.of("依据 lat/lng 标签确定顺序"));
    }

    private CoordinateParseResultDTO fromOrdered(Double longitude, Double latitude, String note) {
        if (!isValidLongitude(longitude) || !isValidLatitude(latitude)) {
            return CoordinateParseResultDTO.unparsed("坐标超出合法范围。");
        }
        return CoordinateParseResultDTO.parsed(longitude, latitude, "经度,纬度", false, List.of(note));
    }

    private CoordinateParseResultDTO inferBareOrder(Double first, Double second) {
        boolean firstAsLongitude = isValidLongitude(first) && isValidLatitude(second);
        boolean secondAsLongitude = isValidLongitude(second) && isValidLatitude(first);

        if (firstAsLongitude && !secondAsLongitude) {
            return CoordinateParseResultDTO.parsed(first, second, "经度,纬度", false,
                    List.of("按数值范围推断为经度,纬度"));
        }
        if (secondAsLongitude && !firstAsLongitude) {
            return CoordinateParseResultDTO.parsed(second, first, "纬度,经度", true,
                    List.of("按数值范围推断为纬度,经度，已自动换序"));
        }
        if (!firstAsLongitude && !secondAsLongitude) {
            return CoordinateParseResultDTO.unparsed("数值不满足经纬度范围，请核对后重试。");
        }
        // 两者都合法（例如都在 73-90 之间）时不能靠范围定序，按最常见约定解析并标记待确认。
        return CoordinateParseResultDTO.parsed(first, second, "经度,纬度", false,
                List.of("两种顺序在数值范围内都成立，已按「经度,纬度」解析，请核对是否为预期地点"));
    }

    /**
     * 度分秒形式，如 117°38'49.2"E 24°30'46.8"N。
     */
    private CoordinateParseResultDTO parseDegreesMinutesSeconds(String text) {
        List<Double> values = new ArrayList<>(2);
        List<String> hemispheres = new ArrayList<>(2);
        Matcher matcher = Pattern.compile(
                "(\\d{1,3})\\s*[°º]\\s*(\\d{1,2})?\\s*['′]?\\s*(\\d+(?:\\.\\d+)?)?\\s*[\"″]?\\s*([NSEWnsew东西南北])?")
                .matcher(text);
        while (matcher.find() && values.size() < 2) {
            double degrees = Double.parseDouble(matcher.group(1));
            double minutes = matcher.group(2) == null ? 0 : Double.parseDouble(matcher.group(2));
            double seconds = matcher.group(3) == null ? 0 : Double.parseDouble(matcher.group(3));
            values.add(degrees + minutes / 60.0 + seconds / 3600.0);
            hemispheres.add(matcher.group(4) == null ? "" : matcher.group(4).toUpperCase(Locale.ROOT));
        }
        if (values.size() < 2) {
            return CoordinateParseResultDTO.unparsed("度分秒格式不完整，请提供经度和纬度两组数值。");
        }

        Double longitude = null;
        Double latitude = null;
        for (int index = 0; index < values.size(); index++) {
            String hemisphere = hemispheres.get(index);
            if ("E".equals(hemisphere) || "东".equals(hemisphere)) {
                longitude = applySign(values.get(index), true, false);
            } else if ("W".equals(hemisphere) || "西".equals(hemisphere)) {
                longitude = applySign(values.get(index), true, true);
            } else if ("N".equals(hemisphere) || "北".equals(hemisphere)) {
                latitude = applySign(values.get(index), false, false);
            } else if ("S".equals(hemisphere) || "南".equals(hemisphere)) {
                latitude = applySign(values.get(index), false, true);
            }
        }
        if (longitude == null || latitude == null) {
            return CoordinateParseResultDTO.unparsed("度分秒缺少东西经或南北纬标识，请补充 E/W 与 N/S。");
        }
        return CoordinateParseResultDTO.parsed(longitude, latitude, "度分秒", false,
                List.of("已从度分秒转换为十进制"));
    }

    private double applySign(double value, boolean longitude, boolean negative) {
        double signed = negative ? -value : value;
        return signed;
    }

    private boolean isValidLongitude(Double value) {
        return value != null && Double.isFinite(value) && Math.abs(value) <= 180.0;
    }

    private boolean isValidLatitude(Double value) {
        return value != null && Double.isFinite(value) && Math.abs(value) <= 90.0;
    }

    private Double toDouble(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static final class CoordinateTextParserHelper {
        private static final Pattern DMS = Pattern.compile("[°º]");

        private static boolean isDegreesMinutesSeconds(String text) {
            return DMS.matcher(text).find();
        }
    }
}
