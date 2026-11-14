import { apiFetch } from '@/api/http';
import type { CoordinateRecognitionResponse, GeocodeSearchResponse } from '@/types/api';

/**
 * 识别粘贴文本中的经纬度并转换为系统契约。
 *
 * @param standard 用户已明确的坐标系（wgs84 / gcj02 / bd09），留空则由服务端自动识别
 */
export function recognizeCoordinate(text: string, standard?: string) {
  return apiFetch<CoordinateRecognitionResponse>('/location/coordinate/recognize', {
    query: standard ? { text, standard } : { text },
  });
}

/**
 * 按地址或地点名称解析坐标，用于用户无法到达现场时按已知地点报单。
 */
export function geocodeAddress(keyword: string) {
  return apiFetch<GeocodeSearchResponse>('/location/geocode', {
    query: { keyword },
  });
}
