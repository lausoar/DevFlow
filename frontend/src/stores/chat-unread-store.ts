import { create } from 'zustand';

// ============================================
// 聊天未读管理（会话级：刷新后清零）
// 由全局 WebSocket 的 /topic/chat.events 事件驱动计数，
// 用户正在查看的频道不计未读，进入频道即清零
// ============================================
interface ChatUnreadState {
  /** channelId → 未读条数 */
  unreadMap: Record<string, number>;
  /** 当前正在查看的频道（查看中不计数） */
  activeChannelId: string | null;
  setActiveChannel: (channelId: string | null) => void;
  increment: (channelId: string) => void;
  clearChannel: (channelId: string) => void;
}

export const useChatUnreadStore = create<ChatUnreadState>((set, get) => ({
  unreadMap: {},
  activeChannelId: null,
  setActiveChannel: (channelId) => set({ activeChannelId: channelId }),
  increment: (channelId) => {
    // 正在看这个频道就不计未读
    if (get().activeChannelId === channelId) return;
    set((state) => ({
      unreadMap: { ...state.unreadMap, [channelId]: (state.unreadMap[channelId] || 0) + 1 },
    }));
  },
  clearChannel: (channelId) =>
    set((state) => {
      if (!state.unreadMap[channelId]) return state;
      const next = { ...state.unreadMap };
      delete next[channelId];
      return { unreadMap: next };
    }),
}));
