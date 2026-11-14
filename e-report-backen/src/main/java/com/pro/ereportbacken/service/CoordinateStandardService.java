package com.pro.ereportbacken.service;

import org.springframework.stereotype.Service;

/**
 * 坐标系识别与转换。
 *
 * <p>国内主流三种标准：WGS84（GPS 原始 / 国际通用）、GCJ-02（国测局火星坐标 / 高德、腾讯）、
 * BD-09（百度在 GCJ-02 之上再次偏移）。系统内部契约统一为 WGS84 存储、GCJ-02 渲染。</p>
 *
 * <p>WGS84 与 GCJ-02 在中国大陆的差值仅数百米，落在同一城市内无法用数值范围可靠区分，
 * 因此本服务只做“候选排序”，由调用方结合地址一致性确认，不做静默判定。</p>
 */
@Service
public class CoordinateStandardService {

    private static final double PI = Math.PI;
    private static final double AXIS = 6378245.0;
    private static final double ECCENTRICITY_SQUARED = 0.00669342162296594323;
    /**
     * BD-09 转换常量。X_PI 是百度公开算法中的角度换算系数 PI * 3000 / 180 ≈ 52.36，
     * 不可简写为 3000 * PI，否则偏移量与官方实现相差数十米。
     */
    private static final double BD09_X_PI = PI * 3000.0 / 180.0;
    private static final double BD09_Z_PI = 0.0065;
    private static final double BD09_Z = 0.006;
    /** 中国大陆粗略外接范围，用于判断是否需要坐标加密偏移。 */
    private static final double CHINA_MIN_LONGITUDE = 72.004;
    private static final double CHINA_MAX_LONGITUDE = 137.8347;
    private static final double CHINA_MIN_LATITUDE = 0.8293;
    private static final double CHINA_MAX_LATITUDE = 55.8271;

    public double[] wgs84ToGcj02(double latitude, double longitude) {
        if (outOfChina(latitude, longitude)) {
            return new double[]{latitude, longitude};
        }
        double deltaLatitude = transformLatitude(longitude - 105.0, latitude - 35.0);
        double deltaLongitude = transformLongitude(longitude - 105.0, latitude - 35.0);
        double radianLatitude = latitude / 180.0 * PI;
        double magic = Math.sin(radianLatitude);
        magic = 1 - ECCENTRICITY_SQUARED * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        double latitudeOffset = (deltaLatitude * 180.0)
                / ((AXIS * (1 - ECCENTRICITY_SQUARED)) / (magic * sqrtMagic) * PI);
        double longitudeOffset = (deltaLongitude * 180.0)
                / (AXIS / sqrtMagic * Math.cos(radianLatitude) * PI);
        return new double[]{latitude + latitudeOffset, longitude + longitudeOffset};
    }

    public double[] gcj02ToWgs84(double latitude, double longitude) {
        if (outOfChina(latitude, longitude)) {
            return new double[]{latitude, longitude};
        }
        // 以 GCJ-02 作为初值迭代逼近，含偏移的输入收敛到对应 WGS84。
        double currentLatitude = latitude;
        double currentLongitude = longitude;
        for (int iteration = 0; iteration < 30; iteration++) {
            double[] approxGcj = wgs84ToGcj02(currentLatitude, currentLongitude);
            double latitudeDelta = approxGcj[0] - latitude;
            double longitudeDelta = approxGcj[1] - longitude;
            if (Math.abs(latitudeDelta) < 1e-9 && Math.abs(longitudeDelta) < 1e-9) {
                break;
            }
            currentLatitude -= latitudeDelta;
            currentLongitude -= longitudeDelta;
        }
        return new double[]{currentLatitude, currentLongitude};
    }

    public double[] gcj02ToBd09(double latitude, double longitude) {
        double z = Math.sqrt(longitude * longitude + latitude * latitude)
                + 0.00002 * Math.sin(latitude * BD09_X_PI);
        double theta = Math.atan2(latitude, longitude) + 0.000003 * Math.cos(longitude * BD09_X_PI);
        double bdLongitude = z * Math.cos(theta) + BD09_Z_PI;
        double bdLatitude = z * Math.sin(theta) + BD09_Z;
        return new double[]{bdLatitude, bdLongitude};
    }

    public double[] bd09ToGcj02(double latitude, double longitude) {
        if (outOfChina(latitude, longitude)) {
            return new double[]{latitude, longitude};
        }
        // 公开的 BD-09 逆变换是近似式，在中国大陆残差可达十米量级。这里以近似解为初值迭代逼近，
        // 使 BD-09 与 GCJ-02 之间可精确往返，避免用户核对时看到无意义的坐标漂移。
        double[] approximation = approxBd09ToGcj02(latitude, longitude);
        double currentLatitude = approximation[0];
        double currentLongitude = approximation[1];
        for (int iteration = 0; iteration < 30; iteration++) {
            double[] forward = gcj02ToBd09(currentLatitude, currentLongitude);
            double latitudeDelta = forward[0] - latitude;
            double longitudeDelta = forward[1] - longitude;
            if (Math.abs(latitudeDelta) < 1e-9 && Math.abs(longitudeDelta) < 1e-9) {
                break;
            }
            currentLatitude -= latitudeDelta;
            currentLongitude -= longitudeDelta;
        }
        return new double[]{currentLatitude, currentLongitude};
    }

    private double[] approxBd09ToGcj02(double latitude, double longitude) {
        double x = longitude - BD09_Z_PI;
        double y = latitude - BD09_Z;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * BD09_X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * BD09_X_PI);
        return new double[]{z * Math.sin(theta), z * Math.cos(theta)};
    }

    public double[] bd09ToWgs84(double latitude, double longitude) {
        double[] gcj = bd09ToGcj02(latitude, longitude);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    public double[] wgs84ToBd09(double latitude, double longitude) {
        double[] gcj = wgs84ToGcj02(latitude, longitude);
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    /**
     * 按指定标准把输入坐标转换到 WGS84，供系统统一存储。
     */
    public double[] toWgs84(CoordinateStandard standard, double latitude, double longitude) {
        return switch (standard) {
            case WGS84 -> new double[]{latitude, longitude};
            case GCJ02 -> gcj02ToWgs84(latitude, longitude);
            case BD09 -> bd09ToWgs84(latitude, longitude);
        };
    }

    public double[] fromWgs84(CoordinateStandard standard, double latitude, double longitude) {
        return switch (standard) {
            case WGS84 -> new double[]{latitude, longitude};
            case GCJ02 -> wgs84ToGcj02(latitude, longitude);
            case BD09 -> wgs84ToBd09(latitude, longitude);
        };
    }

    /**
     * 两点间近似距离（米），使用等距圆柱投影，城市尺度误差可忽略。
     */
    public double distanceMeters(double latitudeA, double longitudeA, double latitudeB, double longitudeB) {
        double meanLatitude = Math.toRadians((latitudeA + latitudeB) / 2.0);
        double deltaLatitude = Math.toRadians(latitudeB - latitudeA);
        double deltaLongitude = Math.toRadians(longitudeB - longitudeA);
        double x = deltaLongitude * Math.cos(meanLatitude);
        return Math.sqrt(x * x + deltaLatitude * deltaLatitude) * 6_371_000.0;
    }

    public boolean outOfChina(double latitude, double longitude) {
        if (longitude < CHINA_MIN_LONGITUDE || longitude > CHINA_MAX_LONGITUDE) {
            return true;
        }
        return latitude < CHINA_MIN_LATITUDE || latitude > CHINA_MAX_LATITUDE;
    }

    private double transformLatitude(double x, double y) {
        double result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        result += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return result;
    }

    private double transformLongitude(double x, double y) {
        double result = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        result += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return result;
    }

    /** 支持的坐标系标准。 */
    public enum CoordinateStandard {
        /** GPS 原始坐标，国际通用。 */
        WGS84("wgs84", "国际通用坐标（GPS 原始）"),
        /** 国测局火星坐标，高德、腾讯使用。 */
        GCJ02("gcj02", "国测局坐标（高德、腾讯）"),
        /** 百度坐标。 */
        BD09("bd09", "百度坐标");

        private final String code;
        private final String displayName;

        CoordinateStandard(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String code() {
            return code;
        }

        public String displayName() {
            return displayName;
        }

        public static CoordinateStandard fromCode(String code) {
            if (code == null) {
                return null;
            }
            String normalized = code.trim().toLowerCase();
            for (CoordinateStandard standard : values()) {
                if (standard.code.equals(normalized)) {
                    return standard;
                }
            }
            return null;
        }
    }
}
