import test from 'node:test';
import assert from 'node:assert/strict';

import {
    COORDINATE_STANDARDS,
    bd09ToGcj02,
    distanceMeters,
    formatContractCoordinates,
    formatDistance,
    fromWgs84,
    gcj02ToBd09,
    gcj02ToWgs84,
    parseContractCoordinates,
    toWgs84,
    wgs84ToGcj02,
} from './coordinateStandards.js';

/** 与后端 CoordinateStandardServiceTests 使用同一参考点，确保两端换算一致。 */
const REFERENCE_LATITUDE = 24.513;
const REFERENCE_LONGITUDE = 117.647;
const CLOSE = 1e-6;

test('WGS84 转 GCJ-02 与高德官方转换结果一致', () => {
    const [gcjLatitude, gcjLongitude] = wgs84ToGcj02(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);

    // 该期望值来自高德 JS API 2.0 的 convertFrom('gps') 实测输出。
    assert.ok(Math.abs(gcjLongitude - 117.651786) < 1e-5, `经度实际 ${gcjLongitude}`);
    assert.ok(Math.abs(gcjLatitude - 24.510021) < 1e-5, `纬度实际 ${gcjLatitude}`);

    const offset = distanceMeters(REFERENCE_LATITUDE, REFERENCE_LONGITUDE, gcjLatitude, gcjLongitude);
    assert.ok(offset > 100 && offset < 900, `偏移应在数百米量级，实际 ${offset}`);
});

test('GCJ-02 转 WGS84 可往返还原', () => {
    const [gcjLatitude, gcjLongitude] = wgs84ToGcj02(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);
    const [wgsLatitude, wgsLongitude] = gcj02ToWgs84(gcjLatitude, gcjLongitude);

    assert.ok(Math.abs(wgsLatitude - REFERENCE_LATITUDE) < CLOSE);
    assert.ok(Math.abs(wgsLongitude - REFERENCE_LONGITUDE) < CLOSE);
});

test('BD-09 与 GCJ-02 之间可精确往返', () => {
    const [bdLatitude, bdLongitude] = gcj02ToBd09(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);
    const [gcjLatitude, gcjLongitude] = bd09ToGcj02(bdLatitude, bdLongitude);

    // 公开逆变换公式存在十米级残差，实现里用迭代逼近消除，因此这里要求亚米级一致。
    assert.ok(Math.abs(gcjLatitude - REFERENCE_LATITUDE) < 1e-5, `纬度实际 ${gcjLatitude}`);
    assert.ok(Math.abs(gcjLongitude - REFERENCE_LONGITUDE) < 1e-5, `经度实际 ${gcjLongitude}`);
});

test('三种标准换算到 WGS84 后互相一致', () => {
    const [gcjLatitude, gcjLongitude] = wgs84ToGcj02(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);
    const [bdLatitude, bdLongitude] = gcj02ToBd09(gcjLatitude, gcjLongitude);

    const [fromGcjLat, fromGcjLon] = toWgs84(COORDINATE_STANDARDS.GCJ02, gcjLatitude, gcjLongitude);
    const [fromBdLat, fromBdLon] = toWgs84(COORDINATE_STANDARDS.BD09, bdLatitude, bdLongitude);

    assert.ok(Math.abs(fromGcjLat - REFERENCE_LATITUDE) < 1e-5);
    assert.ok(Math.abs(fromGcjLon - REFERENCE_LONGITUDE) < 1e-5);
    assert.ok(Math.abs(fromBdLat - REFERENCE_LATITUDE) < 1e-5);
    assert.ok(Math.abs(fromBdLon - REFERENCE_LONGITUDE) < 1e-5);
});

test('境外坐标不做偏移', () => {
    const [latitude, longitude] = wgs84ToGcj02(48.8566, 2.3522);

    assert.equal(latitude, 48.8566);
    assert.equal(longitude, 2.3522);
});

test('fromWgs84 与 toWgs84 互为逆运算', () => {
    for (const standard of [COORDINATE_STANDARDS.WGS84, COORDINATE_STANDARDS.GCJ02, COORDINATE_STANDARDS.BD09]) {
        const [latitude, longitude] = fromWgs84(standard, REFERENCE_LATITUDE, REFERENCE_LONGITUDE);
        const [backLatitude, backLongitude] = toWgs84(standard, latitude, longitude);
        assert.ok(Math.abs(backLatitude - REFERENCE_LATITUDE) < 1e-5, `${standard} 纬度往返失败`);
        assert.ok(Math.abs(backLongitude - REFERENCE_LONGITUDE) < 1e-5, `${standard} 经度往返失败`);
    }
});

test('契约坐标按经度,纬度 格式化与解析', () => {
    assert.equal(formatContractCoordinates(24.513, 117.647), '117.647000,24.513000');

    const parsed = parseContractCoordinates('117.647000,24.513000');
    assert.equal(parsed.longitude, 117.647);
    assert.equal(parsed.latitude, 24.513);
});

test('契约坐标解析拒绝非法输入', () => {
    assert.equal(parseContractCoordinates('117.647'), null);
    assert.equal(parseContractCoordinates('117.647,24.513,1'), null);
    assert.equal(parseContractCoordinates('abc,24.513'), null);
    assert.equal(parseContractCoordinates('200,24.513'), null);
    assert.equal(parseContractCoordinates('117.647,100'), null);
    assert.equal(parseContractCoordinates(null), null);
    assert.equal(parseContractCoordinates(117.647), null);
});

test('距离格式化区分米与公里', () => {
    assert.equal(formatDistance(320), '约 320 米');
    assert.equal(formatDistance(1500), '约 1.50 公里');
    assert.equal(formatDistance(Number.NaN), '');
});
