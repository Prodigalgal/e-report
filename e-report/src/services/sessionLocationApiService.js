import apiClient from './apiClient';

/**
 * 登录态态势地图：在线用户点位与授权范围内工单故障点，坐标已由服务端转为 GCJ-02。
 */
export const fetchSessionSituationMap = () => apiClient.get('/admin/session-situation/map');

/**
 * 识别粘贴文本中的经纬度并转换为系统契约。
 *
 * @param {string} text 用户粘贴的坐标文本
 * @param {string} [standard] 用户已明确的坐标系 code（wgs84 / gcj02 / bd09），留空则自动识别
 */
export const recognizeCoordinate = (text, standard) => apiClient.get('/location/coordinate/recognize', {
    params: standard ? { text, standard } : { text },
});

/**
 * 上报本人当前会话位置，用于后台态势地图。
 *
 * @param {{latitude: number, longitude: number, accuracyMeters?: number, provider?: string}} payload
 */
export const reportSessionLocation = (payload) => apiClient.post('/auth/sessions/location', payload);
