import { useRouter } from 'expo-router';
import { useEffect } from 'react';

import { useAuth } from '@/auth/auth-provider';
import { HomeDashboard } from '@/features/home/home-dashboard';
import { BrandLoading } from '@/ui/brand';

export default function HomeScreen() {
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace('/login');
    }
  }, [isAuthenticated, isLoading, router]);

  if (isLoading || !isAuthenticated) {
    return <BrandLoading message="正在加载账号信息" />;
  }

  return <HomeDashboard />;
}
