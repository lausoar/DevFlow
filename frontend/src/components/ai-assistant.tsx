'use client';

import { useState, useRef, useEffect } from 'react';
import { useAppStore } from '@/stores';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { aiApi, type AiMessage } from '@/lib/api/ai';
import { Bot, Send, Loader2 } from 'lucide-react';

// ============================================
// 全局 AI 助手浮层（顶栏 Bot 按钮触发）
// ============================================
export function AiAssistant() {
  const { aiAssistantOpen, setAiAssistantOpen } = useAppStore();
  const [messages, setMessages] = useState<AiMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim() || loading) return;
    const userMsg: AiMessage = { role: 'user', content: input.trim() };
    const next = [...messages, userMsg];
    setMessages(next);
    setInput('');
    setLoading(true);
    try {
      const res = await aiApi.chat(next);
      setMessages([...next, { role: 'assistant', content: res.data.content }]);
    } catch {
      setMessages([
        ...next,
        { role: 'assistant', content: '抱歉，AI 服务暂时不可用，请稍后再试。' },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Sheet open={aiAssistantOpen} onOpenChange={setAiAssistantOpen}>
      <SheetContent side="right" className="w-full sm:max-w-md">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <Bot className="h-4 w-4 text-primary" />
            AI 助手
          </SheetTitle>
        </SheetHeader>
        <div className="flex flex-1 flex-col overflow-hidden">
          <ScrollArea className="flex-1 px-4 py-3">
            {messages.length === 0 ? (
              <div className="flex min-h-[50vh] flex-col items-center justify-center gap-3 text-center text-muted-foreground">
                <Bot className="h-10 w-10 text-primary/40" />
                <p className="text-sm">我是你的 AI 助手，有什么可以帮你的？</p>
              </div>
            ) : (
              <div className="space-y-3">
                {messages.map((m, i) => (
                  <div
                    key={i}
                    className={`flex gap-2 ${m.role === 'user' ? 'flex-row-reverse' : ''}`}
                  >
                    <div
                      className={`max-w-[80%] whitespace-pre-wrap break-words rounded-lg px-3 py-2 text-sm ${
                        m.role === 'user'
                          ? 'bg-primary text-primary-foreground'
                          : 'bg-muted'
                      }`}
                    >
                      {m.content}
                    </div>
                  </div>
                ))}
                {loading && (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    思考中...
                  </div>
                )}
                <div ref={scrollRef} />
              </div>
            )}
          </ScrollArea>
          <div className="border-t p-3">
            <div className="flex items-center gap-2">
              <Input
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSend();
                  }
                }}
                placeholder="输入消息..."
                disabled={loading}
                className="flex-1"
              />
              <Button
                size="icon"
                onClick={handleSend}
                disabled={!input.trim() || loading}
              >
                {loading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Send className="h-4 w-4" />
                )}
              </Button>
            </div>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
