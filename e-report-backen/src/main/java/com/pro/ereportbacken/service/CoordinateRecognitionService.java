package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.CoordinateRecognitionResponseDTO;
import com.pro.ereportbacken.model.dto.CoordinateRecognitionResponseDTO.CoordinateStandardCandidateDTO;
import com.pro.ereportbacken.model.dto.ReverseGeocodeResponseDTO;
import com.pro.ereportbacken.model.dto.ReverseGeocodeResolutionStatus;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 坐标系识别。
 *
 * <p>国内用户拿到的经纬度可能来自 GPS（WGS84）、高德/腾讯（GCJ-02）或百度（BD-09），
 * 三者相差数百米。仅凭数值范围无法区分 WGS84 与 GCJ-02，因此本服务对三种标准分别做逆地理编码，
 * 以“能否解析出可用地址、以及地址行政区划是否自洽”作为判据排序，并在无法确定时返回全部候选，
 * 由用户在界面上确认。绝不在不确定时静默选一个坐标直接提交。</p>
 */
@Service
@Log4j2
public class CoordinateRecognitionService {

    private final CoordinateStandardService coordinateStandardService;
    private final CoordinateTextParser coordinateTextParser;
    private final LocationService locationService;

    public CoordinateRecognitionService(CoordinateStandardService coordinateStandardService,
                                        CoordinateTextParser coordinateTextParser,
                                        LocationService locationService) {
        this.coordinateStandardService = coordinateStandardService;
        this.coordinateTextParser = coordinateTextParser;
        this.locationService = locationService;
    }

    /**
     * 识别粘贴文本中的坐标并转换到系统契约。
     */
    public CoordinateRecognitionResponseDTO recognizeText(String rawText) {
        return recognizeText(rawText, null);
    }

    /**
     * 识别粘贴文本中的坐标；用户已明确坐标系时按指定标准转换，否则自动识别。
     */
    public CoordinateRecognitionResponseDTO recognizeText(String rawText, String declaredStandard) {
        var parsed = coordinateTextParser.parse(rawText);
        if (!parsed.parsed() || parsed.longitude() == null || parsed.latitude() == null) {
            return empty(parsed.message(), parsed.notes());
        }
        return recognize(parsed.longitude(), parsed.latitude(), declaredStandard, parsed.notes());
    }

    /**
     * 识别指定坐标。
     *
     * @param declaredStandard 用户已明确的坐标系；为空时进入自动识别
     */
    public CoordinateRecognitionResponseDTO recognize(Double longitude,
                                                     Double latitude,
                                                     String declaredStandard,
                                                     List<String> inheritedNotes) {
        List<String> notes = new ArrayList<>(inheritedNotes == null ? List.of() : inheritedNotes);
        if (longitude == null || latitude == null
                || !Double.isFinite(longitude) || !Double.isFinite(latitude)
                || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) {
            return empty("坐标超出合法范围，请核对经度[-180,180]与纬度[-90,90]。", notes);
        }

        CoordinateStandardService.CoordinateStandard declared =
                CoordinateStandardService.CoordinateStandard.fromCode(declaredStandard);

        boolean insideChina = !coordinateStandardService.outOfChina(latitude, longitude);
        if (!insideChina) {
            // 境外不做偏移，三种标准等价，直接按 WGS84 采纳。
            notes.add("坐标位于中国大陆范围之外，不做坐标偏移，按国际通用坐标处理。");
            Candidate candidate = buildCandidate(
                    CoordinateStandardService.CoordinateStandard.WGS84, longitude, latitude, false, true);
            CoordinateStandardCandidateDTO dto = toCandidateDTO(candidate, null, true, 0.0);
            return new CoordinateRecognitionResponseDTO(
                    true, candidate.standard().code(), candidate.standard().displayName(), true,
                    candidate.wgs84Latitude(), candidate.wgs84Longitude(), candidate.gpsCoordinates(),
                    candidate.gcj02Latitude(), candidate.gcj02Longitude(),
                    longitude, latitude,
                    candidate.address(), candidate.township(),
                    List.of(dto), notes, null);
        }

        if (declared != null) {
            Candidate selected = resolveAddress(
                    buildCandidate(declared, longitude, latitude, true, false));
            List<CoordinateStandardCandidateDTO> candidates = List.of(
                    toCandidateDTO(selected, null, true, 0.0));
            notes.add("已按指定坐标系「" + declared.displayName() + "」转换。");
            return assembled(selected, candidates, notes, true);
        }

        List<Candidate> candidates = List.of(
                resolveAddress(buildCandidate(
                        CoordinateStandardService.CoordinateStandard.GCJ02, longitude, latitude, true, false)),
                resolveAddress(buildCandidate(
                        CoordinateStandardService.CoordinateStandard.WGS84, longitude, latitude, true, false)),
                resolveAddress(buildCandidate(
                        CoordinateStandardService.CoordinateStandard.BD09, longitude, latitude, true, false))
        );

        List<Candidate> ranked = candidates.stream()
                .sorted(Comparator.comparingInt(Candidate::rank).thenComparing(Candidate::standard))
                .toList();
        Candidate preferred = ranked.get(0);

        boolean confident = preferred.addressResolved() && (ranked.size() < 2
                || ranked.get(1).rank() > preferred.rank());

        List<CoordinateStandardCandidateDTO> candidateDTOs = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            Candidate candidate = ranked.get(index);
            candidateDTOs.add(toCandidateDTO(
                    candidate,
                    null,
                    index == 0,
                    coordinateStandardService.distanceMeters(
                            preferred.wgs84Latitude(), preferred.wgs84Longitude(),
                            candidate.wgs84Latitude(), candidate.wgs84Longitude())
            ));
        }

        if (!confident) {
            notes.add("三种坐标系都能解析出地址，差异约数百米，请对照下方地址确认后再提交。");
        }

        return new CoordinateRecognitionResponseDTO(
                true,
                confident ? preferred.standard().code() : null,
                confident ? preferred.standard().displayName() : null,
                confident,
                preferred.wgs84Latitude(),
                preferred.wgs84Longitude(),
                preferred.gpsCoordinates(),
                preferred.gcj02Latitude(),
                preferred.gcj02Longitude(),
                longitude,
                latitude,
                preferred.address(),
                preferred.township(),
                candidateDTOs,
                notes,
                confident ? null : "无法自动确定坐标系，请选择与地址相符的一项。");
    }

    private CoordinateRecognitionResponseDTO assembled(Candidate selected,
                                                      List<CoordinateStandardCandidateDTO> candidates,
                                                      List<String> notes,
                                                      boolean confident) {
        return new CoordinateRecognitionResponseDTO(
                true,
                selected.standard().code(),
                selected.standard().displayName(),
                confident,
                selected.wgs84Latitude(),
                selected.wgs84Longitude(),
                selected.gpsCoordinates(),
                selected.gcj02Latitude(),
                selected.gcj02Longitude(),
                selected.sourceLongitude(),
                selected.sourceLatitude(),
                selected.address(),
                selected.township(),
                candidates,
                notes,
                null);
    }

    private Candidate buildCandidate(CoordinateStandardService.CoordinateStandard standard,
                                     double longitude,
                                     double latitude,
                                     boolean resolveAddress,
                                     boolean resolvedByDefault) {
        double[] wgs84 = coordinateStandardService.toWgs84(standard, latitude, longitude);
        double[] gcj02 = coordinateStandardService.fromWgs84(
                CoordinateStandardService.CoordinateStandard.GCJ02, wgs84[0], wgs84[1]);
        String gpsCoordinates = String.format(Locale.ROOT, "%.6f,%.6f", wgs84[1], wgs84[0]);
        return new Candidate(
                standard, longitude, latitude, wgs84[0], wgs84[1], gcj02[0], gcj02[1], gpsCoordinates,
                null, null, resolveAddress && resolvedByDefault, resolvedByDefault, false, 0, "");
    }

    private Candidate resolveAddress(Candidate candidate) {
        try {
            ReverseGeocodeResponseDTO response = locationService.reverseGeocode(candidate.gpsCoordinates());
            boolean resolved = StringUtils.hasText(response.formattedAddress());
            boolean confirmed = response.resolutionStatus() == ReverseGeocodeResolutionStatus.CONFIRMED;
            return candidate.withAddress(
                    response.formattedAddress(),
                    response.township(),
                    resolved,
                    confirmed);
        } catch (RuntimeException exception) {
            log.warn("坐标系候选 {} 逆地理编码失败: {}",
                    candidate.standard().code(), exception.getMessage());
            return candidate.withAddress(null, null, false, false);
        }
    }

    private CoordinateStandardCandidateDTO toCandidateDTO(Candidate candidate,
                                                          String note,
                                                          boolean preferred,
                                                          Double offsetMeters) {
        return new CoordinateStandardCandidateDTO(
                candidate.standard().code(),
                candidate.standard().displayName(),
                candidate.sourceLongitude(),
                candidate.sourceLatitude(),
                candidate.wgs84Latitude(),
                candidate.wgs84Longitude(),
                candidate.gcj02Latitude(),
                candidate.gcj02Longitude(),
                candidate.gpsCoordinates(),
                offsetMeters,
                candidate.address(),
                candidate.township(),
                preferred,
                candidate.addressResolved(),
                note
        );
    }

    private CoordinateRecognitionResponseDTO empty(String message, List<String> notes) {
        return new CoordinateRecognitionResponseDTO(
                false, null, null, false,
                null, null, null, null, null, null, null,
                null, null, List.of(), notes == null ? List.of() : notes, message);
    }

    /**
     * 候选坐标系。rank 越小越优先：能确认地址的最佳，其次能解析地址，最后是完全解析不出地址的。
     */
    private record Candidate(
            CoordinateStandardService.CoordinateStandard standard,
            Double sourceLongitude,
            Double sourceLatitude,
            Double wgs84Latitude,
            Double wgs84Longitude,
            Double gcj02Latitude,
            Double gcj02Longitude,
            String gpsCoordinates,
            String address,
            String township,
            boolean addressResolved,
            boolean addressConfirmed,
            boolean preferred,
            int rank,
            String note
    ) {

        Candidate withAddress(String address, String township, boolean resolved, boolean confirmed) {
            int nextRank = confirmed ? 0 : (resolved ? 1 : 2);
            return new Candidate(
                    standard, sourceLongitude, sourceLatitude,
                    wgs84Latitude, wgs84Longitude, gcj02Latitude, gcj02Longitude,
                    gpsCoordinates, address, township, resolved, confirmed, preferred, nextRank, note);
        }
    }
}
