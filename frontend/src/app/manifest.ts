import type { MetadataRoute } from 'next';

/**
 * PWA manifest。
 *
 * start_url 指向收件箱：移动端安装后的第一屏应该是"待我处理的事"，
 * 而不是需要再点两下的工作台。
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'DevFlow 研发协作',
    short_name: 'DevFlow',
    description: '需求、任务、缺陷、测试与交付的一体化研发协作平台',
    start_url: '/notifications',
    display: 'standalone',
    orientation: 'portrait',
    background_color: '#ffffff',
    theme_color: '#0f172a',
    lang: 'zh-CN',
    icons: [
      { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
      { src: '/icons/icon-maskable.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  };
}
