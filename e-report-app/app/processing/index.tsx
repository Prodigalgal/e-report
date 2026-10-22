import { Redirect, useLocalSearchParams } from 'expo-router';

export default function ProcessingListScreen() {
  const params = useLocalSearchParams<{ type?: string | string[] }>();
  const type = Array.isArray(params.type) ? params.type[0] : params.type;
  return <Redirect href={{ pathname: '/search', params: { ...(type ? { type } : {}), status: '待处理' } }} />;
}
