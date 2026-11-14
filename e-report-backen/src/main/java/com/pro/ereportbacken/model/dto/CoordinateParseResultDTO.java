package com.pro.ereportbacken.model.dto;

import java.util.List;

/**
 * 坐标文本解析结果。顺序字段记录识别到的输入约定，供界面回显给用户核对。
 */
public record CoordinateParseResultDTO(
        boolean parsed,
        /** 解析出的经度，未解析时为 null。 */
        Double longitude,
        /** 解析出的纬度，未解析时为 null。 */
        Double latitude,
        /** 识别到的输入顺序，如「经度,纬度」。 */
        String detectedOrder,
        /** 是否对该输入做过换序修正。 */
        boolean reordered,
        List<String> notes,
        String message
) {

    public static CoordinateParseResultDTO parsed(Double longitude,
                                                 Double latitude,
                                                 String detectedOrder,
                                                 boolean reordered,
                                                 List<String> notes) {
        return new CoordinateParseResultDTO(true, longitude, latitude, detectedOrder, reordered, notes, null);
    }

    public static CoordinateParseResultDTO unparsed(String message) {
        return new CoordinateParseResultDTO(false, null, null, null, false, List.of(), message);
    }
}
