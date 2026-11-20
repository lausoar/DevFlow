import type { Metadata, Viewport } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';
import { Providers } from './providers';
import { PwaRegister } from '@/components/pwa/pwa-register';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

export const metadata: Metadata = {
  title: {
    default: 'DevFlow - 企业级 AI 协作研发平台',
    template: '%s | DevFlow',
  },
  description: '融合飞书 + Jira + Notion + GitHub + AI Agent 的企业研发平台',
  keywords: ['DevFlow', 'AI', '协作', '研发', '项目管理', 'Jira', '飞书'],
  authors: [{ name: 'DevFlow Team' }],
  // PWA：安装到桌面后按独立窗口运行（manifest 由 app/manifest.ts 提供）
  manifest: '/manifest.webmanifest',
  applicationName: 'DevFlow',
  appleWebApp: {
    capable: true,
    title: 'DevFlow',
    statusBarStyle: 'default',
  },
  icons: {
    icon: '/icons/icon-192.png',
    apple: '/icons/icon-192.png',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  // 刘海屏安全区：底部导航不能压在 Home 指示条上
  viewportFit: 'cover',
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#ffffff' },
    { media: '(prefers-color-scheme: dark)', color: '#0f172a' },
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="zh-CN"
      data-scroll-behavior="smooth"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="min-h-full flex flex-col" suppressHydrationWarning>
        <Providers>
          {children}
          <PwaRegister />
        </Providers>
      </body>
    </html>
  );
}
