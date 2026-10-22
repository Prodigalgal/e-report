import NetInfo from '@react-native-community/netinfo';
import { onlineManager } from '@tanstack/react-query';
import { useEffect } from 'react';

export function NetworkStatusProvider({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    return onlineManager.setEventListener((setOnline) => NetInfo.addEventListener((state) => {
      setOnline(Boolean(state.isConnected));
    }));
  }, []);

  return children;
}
