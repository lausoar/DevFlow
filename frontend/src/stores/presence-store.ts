import { create } from 'zustand';

// ============================================
// 在线状态管理
// ============================================
interface PresenceState {
  /** 当前全部在线用户ID（由 WebSocket 实时推送维护） */
  onlineUserIds: string[];
  setOnlineUserIds: (ids: string[]) => void;
}

export const usePresenceStore = create<PresenceState>((set) => ({
  onlineUserIds: [],
  setOnlineUserIds: (ids) => {
    if (typeof window !== 'undefined') console.log('[presence] set', ids.length, ids.slice(0, 4));
    set({ onlineUserIds: ids });
  },
}));
