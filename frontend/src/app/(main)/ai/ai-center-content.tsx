'use client';

import { useState, useRef, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { aiApi, type AiMessage, type AgentResult, type RagSearchResult } from '@/lib/api/ai';
import { aiChatHistoryApi, aiKindLabels, persistAiMessage, type AiChatHistoryItem } from '@/lib/api/ai-history';
import {
  Bot,
  Send,
  Sparkles,
  Code2,
  BookOpen,
  Zap,
  FileText,
  Search,
  Loader2,
  Copy,
  Check,
  Trash2,
  Lightbulb,
} from 'lucide-react';

// ============================================
// AI 中心内容
// ============================================
export default function AiCenterContent() {
  const [activeTab, setActiveTab] = useState('chat');

  return (
    <div className="flex h-full flex-col">
      {/* 头部 */}
      <div className="flex items-center gap-3 border-b px-6 py-4">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600">
          <Sparkles className="h-5 w-5 text-white" />
        </div>
        <div>
          <h1 className="text-lg font-semibold">AI 中心</h1>
          <p className="text-xs text-muted-foreground">RAG · Agent · Code Review · 智能助手</p>
        </div>
      </div>

      {/* 功能标签页 */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="flex flex-1 flex-col overflow-hidden">
        <div className="border-b px-6">
          <TabsList className="h-10 bg-transparent">
            <TabsTrigger value="chat" className="gap-1.5 text-xs">
              <Bot className="h-3.5 w-3.5" />
              AI 对话
            </TabsTrigger>
            <TabsTrigger value="agent" className="gap-1.5 text-xs">
              <Zap className="h-3.5 w-3.5" />
              Agent
            </TabsTrigger>
            <TabsTrigger value="rag" className="gap-1.5 text-xs">
              <BookOpen className="h-3.5 w-3.5" />
              知识库
            </TabsTrigger>
            <TabsTrigger value="review" className="gap-1.5 text-xs">
              <Code2 className="h-3.5 w-3.5" />
              Code Review
            </TabsTrigger>
            <TabsTrigger value="assistant" className="gap-1.5 text-xs">
              <Lightbulb className="h-3.5 w-3.5" />
              助手
            </TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="chat" className="min-h-0 flex-1 overflow-hidden">
          <AiChatPanel />
        </TabsContent>
        <TabsContent value="agent" className="flex-1 overflow-auto p-6">
          <AgentPanel />
        </TabsContent>
        <TabsContent value="rag" className="flex-1 overflow-auto p-6">
          <RagPanel />
        </TabsContent>
        <TabsContent value="review" className="flex-1 overflow-auto p-6">
          <CodeReviewPanel />
        </TabsContent>
        <TabsContent value="assistant" className="flex-1 overflow-auto p-6">
          <AssistantPanel />
        </TabsContent>
      </Tabs>
    </div>
  );
}

// ============================================
// AI 对话面板（消息按用户账号持久化）
// ============================================
function AiChatPanel() {
  const [messages, setMessages] = useState<AiMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [clearing, setClearing] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // 进入页面时加载该账号的历史记录
  useEffect(() => {
    let cancelled = false;
    aiChatHistoryApi
      .history(100)
      .then((res) => {
        if (cancelled) return;
        const items: AiChatHistoryItem[] = res.data || [];
        setMessages(
          items.map((m) => ({
            role: m.role,
            // 非 CHAT 行为在内容前加类型标签，便于回看历史
            content: aiKindLabels[m.kind] ? `【${aiKindLabels[m.kind]}】\n${m.content}` : m.content,
          }))
        );
        setHistoryLoading(false);
      })
      .catch(() => !cancelled && setHistoryLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, historyLoading]);

  const handleClear = async () => {
    setClearing(true);
    try {
      await aiChatHistoryApi.clear();
      setMessages([]);
    } catch {
      // 清空失败静默
    } finally {
      setClearing(false);
    }
  };

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMsg: AiMessage = { role: 'user', content: input };
    const newMessages = [...messages, userMsg];
    setMessages(newMessages);
    setInput('');
    setLoading(true);
    persistAiMessage('user', input, 'CHAT');

    try {
      const res = await aiApi.chat(newMessages);
      const assistantMsg: AiMessage = {
        role: 'assistant',
        content: res.data.content,
      };
      setMessages([...newMessages, assistantMsg]);
      persistAiMessage('assistant', res.data.content, 'CHAT', res.data.model);
    } catch (e: unknown) {
      const isTimeout =
        (e as { code?: string })?.code === 'ECONNABORTED' ||
        String((e as { message?: string })?.message || '').includes('timeout');
      const fallback = isTimeout
        ? 'AI 回复超时（模型响应较慢），请稍后重试或换个简短的问题。'
        : '抱歉，AI 服务暂时不可用，请稍后再试。';
      setMessages([...newMessages, { role: 'assistant', content: fallback }]);
      persistAiMessage('assistant', fallback, 'CHAT');
    } finally {
      setLoading(false);
    }
  };

  const quickPrompts = [
    '帮我生成今日工作日报',
    '解释这段代码的作用',
    '分析这个 Bug 的可能原因',
    '将需求拆解为任务列表',
  ];

  return (
    <div className="flex h-full flex-col">
      {/* 消息列表 */}
      <ScrollArea className="min-h-0 flex-1 px-6 py-4">
        {historyLoading ? (
          <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            正在加载历史记录...
          </div>
        ) : messages.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-4 text-muted-foreground">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500/20 to-purple-600/20">
              <Sparkles className="h-7 w-7 text-indigo-500" />
            </div>
            <p className="text-sm">有什么可以帮助你的？</p>
            <div className="mt-2 grid grid-cols-2 gap-2">
              {quickPrompts.map((prompt) => (
                <button
                  key={prompt}
                  onClick={() => setInput(prompt)}
                  className="rounded-lg border px-3 py-2 text-left text-xs text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            {messages.map((msg, i) => (
              <ChatBubble key={i} message={msg} />
            ))}
            {loading && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                AI 正在思考...
              </div>
            )}
            <div ref={scrollRef} />
          </div>
        )}
      </ScrollArea>

      {/* 输入区 */}
      <div className="border-t px-6 py-3">
        <div className="flex items-center gap-2">
          <Input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
            placeholder="输入消息..."
            disabled={loading}
            className="flex-1"
          />
          <Button
            onClick={handleSend}
            disabled={!input.trim() || loading}
            size="icon"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
          </Button>
          {messages.length > 0 && (
            <Button variant="ghost" size="icon" onClick={handleClear} disabled={clearing} title="清空历史记录">
              {clearing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

// ============================================
// 聊天气泡
// ============================================
function ChatBubble({ message }: { message: AiMessage }) {
  const [copied, setCopied] = useState(false);
  const isUser = message.role === 'user';

  const handleCopy = () => {
    navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : ''}`}>
      {/* 头像 */}
      <div
        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
          isUser
            ? 'bg-primary text-primary-foreground'
            : 'bg-gradient-to-br from-indigo-500 to-purple-600 text-white'
        }`}
      >
        {isUser ? 'U' : <Bot className="h-4 w-4" />}
      </div>

      {/* 消息内容 */}
      <div
        className={`group relative max-w-[75%] rounded-2xl px-4 py-2.5 text-sm ${
          isUser
            ? 'bg-primary text-primary-foreground'
            : 'bg-muted'
        }`}
      >
        <div className="whitespace-pre-wrap break-words">{message.content}</div>

        {/* 复制按钮 */}
        {!isUser && (
          <button
            onClick={handleCopy}
            className="absolute -bottom-6 right-0 rounded p-1 opacity-0 group-hover:opacity-100 transition-opacity"
          >
            {copied ? (
              <Check className="h-3 w-3 text-green-500" />
            ) : (
              <Copy className="h-3 w-3 text-muted-foreground" />
            )}
          </button>
        )}
      </div>
    </div>
  );
}

// ============================================
// Agent 面板
// ============================================
function AgentPanel() {
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<AgentResult | null>(null);
  const [loading, setLoading] = useState(false);

  const handleRun = async () => {
    if (!query.trim() || loading) return;
    setLoading(true);
    persistAiMessage('user', query, 'AGENT');
    try {
      const res = await aiApi.agentRun(query);
      setResult(res.data);
      persistAiMessage('assistant', res.data.answer, 'AGENT', res.data.model);
    } catch {
      const fallback = 'Agent 运行失败，请检查 AI 服务是否可用。';
      setResult({ answer: fallback, tool_calls: [], model: 'error' });
      persistAiMessage('assistant', fallback, 'AGENT');
    } finally {
      setLoading(false);
    }
  };

  const exampleQueries = [
    '搜索知识库中关于 API 设计的文档',
    '帮我分析这段代码的性能问题：const data = await fetch("/api").then(r => r.json())',
    '把"用户登录功能"拆解为具体的开发任务',
  ];

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h2 className="mb-1 text-base font-semibold">AI Agent</h2>
        <p className="text-xs text-muted-foreground">
          Agent 会自动选择工具（知识库搜索、代码分析、任务生成）完成任务
        </p>
      </div>

      {/* 输入 */}
      <div className="flex gap-2">
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleRun()}
          placeholder="描述你的任务，Agent 会自动执行..."
          className="flex-1"
        />
        <Button onClick={handleRun} disabled={!query.trim() || loading}>
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Zap className="h-4 w-4" />}
          <span className="ml-1.5">运行</span>
        </Button>
      </div>

      {/* 示例 */}
      {!result && !loading && (
        <div className="space-y-2">
          <p className="text-xs font-medium text-muted-foreground">示例：</p>
          {exampleQueries.map((q) => (
            <button
              key={q}
              onClick={() => setQuery(q)}
              className="block w-full rounded-lg border px-3 py-2 text-left text-xs text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
            >
              {q}
            </button>
          ))}
        </div>
      )}

      {/* 结果 */}
      {loading && (
        <Card>
          <CardContent className="flex items-center gap-3 py-6">
            <Loader2 className="h-5 w-5 animate-spin text-primary" />
            <span className="text-sm text-muted-foreground">Agent 正在分析并执行工具...</span>
          </CardContent>
        </Card>
      )}

      {result && !loading && (
        <div className="space-y-4">
          {/* 工具调用记录 */}
          {result.tool_calls.length > 0 && (
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-xs text-muted-foreground">工具调用</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                {result.tool_calls.map((tc, i) => (
                  <div key={i} className="flex items-center gap-2 rounded-md bg-muted/50 p-2">
                    <Zap className="h-3.5 w-3.5 text-amber-500" />
                    <span className="text-xs font-medium">{tc.tool}</span>
                    <Badge variant="outline" className="text-[10px]">
                      已完成
                    </Badge>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}

          {/* 最终回答 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">Agent 回答</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="whitespace-pre-wrap text-sm">{result.answer}</div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}

// ============================================
// RAG 知识库搜索面板
// ============================================
function RagPanel() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<RagSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const handleSearch = async () => {
    if (!query.trim() || loading) return;
    setLoading(true);
    setSearched(true);
    persistAiMessage('user', query, 'RAG');
    try {
      const res = await aiApi.ragSearch(query);
      setResults(res.data);
      const summary = res.data.length
        ? '命中文档：\n' + res.data.map((r, i) => `${i + 1}. ${r.metadata?.title || '未命名'}（相似度 ${r.score.toFixed(3)}）`).join('\n')
        : '未命中任何文档。';
      persistAiMessage('assistant', summary, 'RAG');
    } catch {
      setResults([]);
      persistAiMessage('assistant', '知识库检索失败。', 'RAG');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h2 className="mb-1 text-base font-semibold">知识库搜索</h2>
        <p className="text-xs text-muted-foreground">
          基于向量检索，从企业文档中搜索相关信息
        </p>
      </div>

      {/* 搜索输入 */}
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            placeholder="输入关键词搜索知识库..."
            className="pl-9"
          />
        </div>
        <Button onClick={handleSearch} disabled={!query.trim() || loading}>
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
          <span className="ml-1.5">搜索</span>
        </Button>
      </div>

      {/* 搜索结果 */}
      {searched && !loading && results.length === 0 && (
        <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
          <BookOpen className="h-10 w-10 opacity-30" />
          <p className="text-sm">未找到相关内容</p>
        </div>
      )}

      {results.length > 0 && (
        <div className="space-y-3">
          {results.map((r, i) => (
            <Card key={i}>
              <CardContent className="p-4">
                <div className="mb-2 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <FileText className="h-3.5 w-3.5 text-muted-foreground" />
                    <span className="text-xs font-medium">{r.metadata.title || '未知文档'}</span>
                  </div>
                  <Badge variant="outline" className="text-[10px]">
                    相似度 {(r.score * 100).toFixed(0)}%
                  </Badge>
                </div>
                <p className="text-sm text-muted-foreground line-clamp-4">{r.content}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

// ============================================
// Code Review 面板
// ============================================
function CodeReviewPanel() {
  const [code, setCode] = useState('');
  const [language, setLanguage] = useState('typescript');
  const [context, setContext] = useState('');
  const [review, setReview] = useState('');
  const [loading, setLoading] = useState(false);

  const handleReview = async () => {
    if (!code.trim() || loading) return;
    setLoading(true);
    persistAiMessage('user', `【${language}】\n${code.slice(0, 2000)}`, 'REVIEW');
    try {
      const res = await aiApi.codeReview(code, language, context || undefined);
      setReview(res.data.review);
      persistAiMessage('assistant', res.data.review, 'REVIEW', res.data.model);
    } catch {
      const fallback = '代码审查失败，请检查 AI 服务是否可用。';
      setReview(fallback);
      persistAiMessage('assistant', fallback, 'REVIEW');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl space-y-4">
      <div>
        <h2 className="mb-1 text-base font-semibold">AI Code Review</h2>
        <p className="text-xs text-muted-foreground">
          从 Bug 风险、安全、性能、代码质量等维度审查代码
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {/* 左侧：代码输入 */}
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              className="flex h-8 rounded-md border border-input bg-background px-2 text-xs"
            >
              <option value="typescript">TypeScript</option>
              <option value="javascript">JavaScript</option>
              <option value="java">Java</option>
              <option value="python">Python</option>
              <option value="go">Go</option>
              <option value="rust">Rust</option>
            </select>
            <Input
              value={context}
              onChange={(e) => setContext(e.target.value)}
              placeholder="上下文说明（可选）"
              className="h-8 text-xs"
            />
          </div>
          <textarea
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="粘贴代码..."
            className="h-80 w-full resize-none rounded-md border bg-muted/30 p-3 font-mono text-xs focus:outline-none focus:ring-2 focus:ring-ring"
          />
          <Button onClick={handleReview} disabled={!code.trim() || loading} className="w-full">
            {loading ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : <Code2 className="mr-1.5 h-4 w-4" />}
            {loading ? '审查中...' : '开始审查'}
          </Button>
        </div>

        {/* 右侧：审查结果 */}
        <div className="rounded-md border bg-card">
          <div className="border-b px-3 py-2">
            <span className="text-xs font-medium">审查结果</span>
          </div>
          <ScrollArea className="h-80 p-3">
            {review ? (
              <div className="whitespace-pre-wrap text-xs leading-relaxed">{review}</div>
            ) : (
              <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
                粘贴代码后点击"开始审查"
              </div>
            )}
          </ScrollArea>
        </div>
      </div>
    </div>
  );
}

// ============================================
// 助手面板（日报/总结/任务生成）
// ============================================
function AssistantPanel() {
  const [mode, setMode] = useState<'daily' | 'summary' | 'tasks'>('daily');
  const [input, setInput] = useState('');
  const [output, setOutput] = useState('');
  const [loading, setLoading] = useState(false);

  const handleGenerate = async () => {
    setLoading(true);
    const modeLabels = { daily: 'REPORT', summary: 'SUMMARY', tasks: 'TASKS' } as const;
    const kind = modeLabels[mode];
    try {
      let result: string;
      switch (mode) {
        case 'daily':
          const dailyRes = await aiApi.dailyReport('current-user');
          result = dailyRes.data.report;
          break;
        case 'summary':
          if (!input.trim()) return;
          const summaryRes = await aiApi.summary(input);
          result = summaryRes.data.summary;
          break;
        case 'tasks':
          if (!input.trim()) return;
          const tasksRes = await aiApi.generateTasks(input);
          result = tasksRes.data.tasks_raw;
          break;
      }
      setOutput(result);
      persistAiMessage('user', mode === 'daily' ? '生成今日日报' : input, kind);
      persistAiMessage('assistant', result, kind);
    } catch {
      const fallback = '生成失败，请检查 AI 服务是否可用。';
      setOutput(fallback);
      persistAiMessage('assistant', fallback, kind);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div>
        <h2 className="mb-1 text-base font-semibold">AI 助手</h2>
        <p className="text-xs text-muted-foreground">日报生成、内容总结、任务拆解</p>
      </div>

      {/* 模式选择 */}
      <div className="flex gap-2">
        {[
          { key: 'daily' as const, label: '日报', icon: <FileText className="h-3.5 w-3.5" /> },
          { key: 'summary' as const, label: '总结', icon: <BookOpen className="h-3.5 w-3.5" /> },
          { key: 'tasks' as const, label: '任务拆解', icon: <Zap className="h-3.5 w-3.5" /> },
        ].map((m) => (
          <button
            key={m.key}
            onClick={() => { setMode(m.key); setOutput(''); }}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
              mode === m.key
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted text-muted-foreground hover:bg-muted/80'
            }`}
          >
            {m.icon}
            {m.label}
          </button>
        ))}
      </div>

      {/* 输入 */}
      {mode !== 'daily' && (
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={mode === 'summary' ? '粘贴要总结的内容...' : '输入需求描述...'}
          className="h-32 w-full resize-none rounded-md border bg-background p-3 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
        />
      )}

      <Button onClick={handleGenerate} disabled={loading || (mode !== 'daily' && !input.trim())} className="w-full">
        {loading ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : <Sparkles className="mr-1.5 h-4 w-4" />}
        {loading ? '生成中...' : '生成'}
      </Button>

      {/* 输出 */}
      {output && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">
              {mode === 'daily' ? '今日日报' : mode === 'summary' ? '总结结果' : '任务列表'}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="whitespace-pre-wrap text-sm">{output}</div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
