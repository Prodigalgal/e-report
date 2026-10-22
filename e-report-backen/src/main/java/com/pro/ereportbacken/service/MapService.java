package com.pro.ereportbacken.service;

import org.springframework.stereotype.Service;

@Service
public class MapService {
// GCJ-02（火星坐标系）和 WGS-84（GPS坐标系）互转工具类

    private static final double PI = Math.PI;
    private static final double AXIS = 6378245.0; // 克拉索夫斯基椭球体长半轴a
    private static final double ECCENTRICITY_SQUARED = 0.00669342162296594323; // 椭球体第一偏心率的平方 ee = (a^2 - b^2) / a^2

    /**
     * 判断坐标是否在中国境外
     * @param lat 纬度
     * @param lon 经度
     * @return true 如果在中国境外，false 如果在中国境内
     */
    private boolean outOfChina(double lat, double lon) {
        if (lon < 72.004 || lon > 137.8347) {
            return true;
        }
        return lat < 0.8293 || lat > 55.8271;
    }

    /**
     * 计算纬度方向的偏移量（米）
     * WGS-84 到 GCJ-02 的核心算法部分
     * @param x 经度减去105后的值
     * @param y 纬度减去35后的值
     * @return 纬度偏移量（米）
     */
    private double transformLatInternal(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * 计算经度方向的偏移量（米）
     * WGS-84 到 GCJ-02 的核心算法部分
     * @param x 经度减去105后的值
     * @param y 纬度减去35后的值
     * @return 经度偏移量（米）
     */
    private double transformLonInternal(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * WGS-84 坐标转换为 GCJ-02 坐标 (火星坐标)
     *
     * @param wgsLat WGS-84 纬度
     * @param wgsLon WGS-84 经度
     * @return GCJ-02 坐标对象 {lat, lon}
     */
    public Point wgs84ToGcj02(double wgsLat, double wgsLon) {
        if (outOfChina(wgsLat, wgsLon)) {
            return new Point(wgsLat, wgsLon);
        }

        double dLatMeters = transformLatInternal(wgsLon - 105.0, wgsLat - 35.0);
        double dLonMeters = transformLonInternal(wgsLon - 105.0, wgsLat - 35.0);

        double radLat = wgsLat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - ECCENTRICITY_SQUARED * magic * magic;
        double sqrtMagic = Math.sqrt(magic);

        // 将米单位的偏移量转换为度单位
        double dLat = (dLatMeters * 180.0) / ((AXIS * (1 - ECCENTRICITY_SQUARED)) / (magic * sqrtMagic) * PI);
        double dLon = (dLonMeters * 180.0) / (AXIS / sqrtMagic * Math.cos(radLat) * PI);

        double gcjLat = wgsLat + dLat;
        double gcjLon = wgsLon + dLon;

        return new Point(gcjLat, gcjLon);
    }

    public Point wgs84ToGcj02(String wgsLat, String wgsLon) {
        return wgs84ToGcj02(Double.parseDouble(wgsLat), Double.parseDouble(wgsLon));
    }

    /**
     * GCJ-02 坐标 (火星坐标) 转换为 WGS-84 坐标
     * 采用迭代逼近法
     *
     * @param gcjLat GCJ-02 纬度
     * @param gcjLon GCJ-02 经度
     * @return WGS-84 坐标对象 {lat, lon}
     */
    public Point gcj02ToWgs84(double gcjLat, double gcjLon) {
        if (outOfChina(gcjLat, gcjLon)) {
            return new Point(gcjLat, gcjLon);
        }

        double currentWgsLat = gcjLat;
        double currentWgsLon = gcjLon;
        Point tempGcjPoint;

        // 迭代次数可以根据精度需求调整，通常几次迭代后精度已足够
        // 也可以设置一个精度阈值 (threshold) 来判断收敛
        int maxIterations = 100; // 最大迭代次数
        double threshold = 1e-7; // 1e-7度约等于1cm的精度

        for (int i = 0; i < maxIterations; i++) {
            tempGcjPoint = wgs84ToGcj02(currentWgsLat, currentWgsLon); // 使用当前WGS84估计值转换回GCJ02

            // 计算当前WGS84估计值转换到GCJ02后的坐标 与 实际GCJ02坐标 的差值，这个差值就是偏移量
            // 我们希望找到一个WGS84坐标，使得 (WGS_lat + dLat, WGS_lon + dLon) = (GCJ_lat, GCJ_lon)
            // 所以 WGS_lat = GCJ_lat - dLat, WGS_lon = GCJ_lon - dLon
            // dLat 和 dLon 是基于当前 WGS_lat_guess, WGS_lon_guess 计算出来的
            double dLat = tempGcjPoint.lat - currentWgsLat; // 这是根据 currentWgsLat, currentWgsLon 计算出的纬度偏移
            double dLon = tempGcjPoint.lon - currentWgsLon; // 这是根据 currentWgsLat, currentWgsLon 计算出的经度偏移

            double newWgsLat = gcjLat - dLat;
            double newWgsLon = gcjLon - dLon;

            // 检查是否收敛
            if (Math.abs(newWgsLat - currentWgsLat) < threshold && Math.abs(newWgsLon - currentWgsLon) < threshold) {
                currentWgsLat = newWgsLat;
                currentWgsLon = newWgsLon;
                break; // 已达到所需精度
            }

            currentWgsLat = newWgsLat;
            currentWgsLon = newWgsLon;
        }
        return new Point(currentWgsLat, currentWgsLon);
    }

    /**
     * GCJ-02 坐标 (火星坐标) 转换为 WGS-84 坐标 (精确版，但可能速度稍慢)
     * 这是另一种常见的迭代逼近实现，与上面的 gcj02ToWgs84 效果类似。
     * 实践中，上述 `gcj02ToWgs84` 的迭代方法通常收敛更快。
     * 这个版本也被称为 "EvilTransform" 的逆转换。
     * @param gcjLat GCJ-02 纬度
     * @param gcjLon GCJ-02 经度
     * @return WGS-84 坐标对象 {lat, lon}
     */
    public Point gcj02ToWgs84Exact(double gcjLat, double gcjLon) {
        if (outOfChina(gcjLat, gcjLon)) {
            return new Point(gcjLat, gcjLon);
        }
        Point initDelta = wgs84ToGcj02(gcjLat, gcjLon); // 将GCJ坐标当成WGS坐标，计算一次转换，得到一个近似的偏移量
        double dLat = initDelta.lat - gcjLat;
        double dLon = initDelta.lon - gcjLon;

        // 用GCJ坐标减去这个近似的偏移量，得到一个初步的WGS坐标
        double wgsLat = gcjLat - dLat;
        double wgsLon = gcjLon - dLon;

        return new Point(wgsLat, wgsLon);
    }


    // 辅助类，用于存储经纬度对
    public static class Point {
        public double lat;
        public double lon;

        public Point(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }

        @Override
        public String toString() {
            return "Point{" +
                    "lat=" + String.format("%.6f", lat) + // 格式化输出，保留6位小数
                    ", lon=" + String.format("%.6f", lon) +
                    '}';
        }
    }
}
