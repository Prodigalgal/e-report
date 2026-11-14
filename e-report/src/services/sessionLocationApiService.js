import apiClient from './apiClient';

/**
 * 登录态态势地图：在线用户点位与授权范围内工单故障点，坐标已由服务端转为 GCJ-02。
 *
 * 需要 `user:session-location:view` 权限，仅管理员持有。
 */
export const fetchSessionSituationMap = () => apiClient.get('/admin/session-situation/map');

/**
 * 坐标提取器所需的地图渲染凭据。
 *
 * 与管理员地图接口分开：报单人员不持有地点查看权限，若复用上面那条会 403，
 * 导致提取器里不依赖地图的粘贴识别路径也一并失效。此接口只返回地图密钥，不含任何位置数据。
 */
export const fetchMapConfig = () => apiClient.get('/location/map-config');

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
