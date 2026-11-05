import { create } from 'zustand';

// ============================================
// 应用全局状态管理
// ============================================
interface AppState {
  // 侧边栏
  sidebarCollapsed: boolean;
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;

  // 右侧面板
  rightPanelOpen: boolean;
  setRightPanelOpen: (open: boolean) => void;

  // 全局搜索
  searchOpen: boolean;
  setSearchOpen: (open: boolean) => void;

  // 当前项目
  currentProjectId: string | null;
  setCurrentProjectId: (id: string | null) => void;

  // AI 助手面板
  aiAssistantOpen: boolean;
  setAiAssistantOpen: (open: boolean) => void;

  // 移动端菜单
  mobileMenuOpen: boolean;
  setMobileMenuOpen: (open: boolean) => void;
}

export const useAppStore = create<AppState>()((set) => ({
  // 侧边栏
  sidebarCollapsed: false,
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
  setSidebarCollapsed: (collapsed) => set({ sidebarCollapsed: collapsed }),

  // 右侧面板
  rightPanelOpen: false,
  setRightPanelOpen: (open) => set({ rightPanelOpen: open }),

  // 全局搜索
  searchOpen: false,
  setSearchOpen: (open) => set({ searchOpen: open }),

  // 当前项目
  currentProjectId: null,
  setCurrentProjectId: (id) => set({ currentProjectId: id }),

  // AI 助手面板
  aiAssistantOpen: false,
  setAiAssistantOpen: (open) => set({ aiAssistantOpen: open }),

  // 移动端菜单
  mobileMenuOpen: false,
  setMobileMenuOpen: (open) => set({ mobileMenuOpen: open }),
}));
