import { apiFetch } from '@/api/http';

/**
 * 上报本人当前会话位置，供后台态势地图展示在岗人员分布。
 *
 * 坐标使用 WGS84（系统存储契约）；服务端会校验范围，且只保留该会话的最新位置。
 * `fixedAt` 是定位时刻（UTC，无时区后缀），用于让服务端区分新采集与缓存位置。
 */
export function reportSessionLocation(payload: {
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
  provider?: string;
  fixedAt?: string;
}) {
  return apiFetch<void>('/auth/sessions/location', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
