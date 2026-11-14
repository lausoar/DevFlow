import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Docker 部署需要 standalone 产物（只含运行所需的最小 node_modules）
  output: 'standalone',
};

export default nextConfig;
