import { createContext, useCallback, useContext, useMemo, useState } from 'react';

type AppMenuState = {
  sidebarExpanded: boolean;
  toggleSidebar: () => void;
  closeSidebar: () => void;
};

const AppMenuContext = createContext<AppMenuState | null>(null);

export function AppMenuProvider({ children }: { children: React.ReactNode }) {
  const [sidebarExpanded, setSidebarExpanded] = useState(false);

  const toggleSidebar = useCallback(() => {
    setSidebarExpanded((value) => !value);
  }, []);

  const closeSidebar = useCallback(() => {
    setSidebarExpanded(false);
  }, []);

  const value = useMemo<AppMenuState>(() => ({
    sidebarExpanded,
    toggleSidebar,
    closeSidebar,
  }), [closeSidebar, sidebarExpanded, toggleSidebar]);

  return <AppMenuContext.Provider value={value}>{children}</AppMenuContext.Provider>;
}

export function useAppMenu() {
  const context = useContext(AppMenuContext);
  if (!context) {
    throw new Error('useAppMenu must be used inside AppMenuProvider.');
  }
  return context;
}
