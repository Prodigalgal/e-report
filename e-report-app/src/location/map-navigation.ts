import { Linking, NativeModules, Platform, Share } from 'react-native';

type MapPoint = {
  longitude: number;
  latitude: number;
};

type NativeEReportNavigationModule = {
  openNavigationChooser(
    longitude: number,
    latitude: number,
    destinationName: string,
  ): Promise<boolean>;
};

const NativeEReportNavigation = NativeModules.EReportNavigation as NativeEReportNavigationModule | undefined;

export async function openExternalNavigation(gpsCoordinates?: string, destinationName = '工单现场') {
  const point = parseGpsCoordinates(gpsCoordinates);
  if (!point) {
    throw new Error('工单没有有效定位坐标，请先补录经度、纬度后再导航。');
  }

  if (Platform.OS === 'android' && NativeEReportNavigation?.openNavigationChooser) {
    await NativeEReportNavigation.openNavigationChooser(
      point.longitude,
      point.latitude,
      destinationName.trim() || '工单现场',
    );
    return;
  }

  if (Platform.OS === 'ios') {
    const coordinate = `${point.latitude},${point.longitude}`;
    const appleMapsUrl = `maps://?daddr=${coordinate}&q=${encodeURIComponent(destinationName)}`;
    if (await Linking.canOpenURL(appleMapsUrl)) {
      await Linking.openURL(appleMapsUrl);
      return;
    }
  }

  await Linking.openURL(buildAmapWebNavigationUrl(point, destinationName));
}

export async function shareWorkOrderLocation(
  workOrderId: string,
  gpsCoordinates?: string,
  destinationName = '工单现场',
) {
  const point = parseGpsCoordinates(gpsCoordinates);
  if (!point) {
    throw new Error('工单没有有效定位坐标，请先补录经度、纬度后再分享。');
  }
  const title = `工单 #${workOrderId} · ${destinationName || '工单现场'}`;
  await Share.share({
    title,
    message: `${title}\n${buildAmapMarkerUrl(point, destinationName)}`,
  });
}

export function parseGpsCoordinates(gpsCoordinates?: string): MapPoint | null {
  if (!gpsCoordinates) {
    return null;
  }
  const parts = gpsCoordinates.split(',').map((value) => Number(value.trim()));
  if (parts.length !== 2 || parts.some((value) => !Number.isFinite(value))) {
    return null;
  }
  const [longitude, latitude] = parts;
  if (Math.abs(longitude) > 180 || Math.abs(latitude) > 90) {
    return null;
  }
  return { longitude, latitude };
}

function buildAmapWebNavigationUrl(point: MapPoint, destinationName: string) {
  const destination = encodeURIComponent(`${point.longitude},${point.latitude},${destinationName}`);
  return `https://uri.amap.com/navigation?to=${destination}&mode=car&coordinate=wgs84&callnative=1&src=e-report-app`;
}

function buildAmapMarkerUrl(point: MapPoint, destinationName: string) {
  const position = encodeURIComponent(`${point.longitude},${point.latitude}`);
  return `https://uri.amap.com/marker?position=${position}&name=${encodeURIComponent(destinationName || '工单现场')}&coordinate=wgs84&callnative=1&src=e-report-app`;
}
