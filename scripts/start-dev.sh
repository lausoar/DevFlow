#!/bin/bash
# ============================================
# DevFlow 开发环境启动脚本
# ============================================

echo "🚀 DevFlow 开发环境启动..."
echo ""

# 检查依赖
command -v node >/dev/null 2>&1 || { echo "❌ 请先安装 Node.js"; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "❌ 请先安装 Python 3.11+"; exit 1; }

# 启动前端
echo "📦 启动前端 (Next.js)..."
cd frontend && npm run dev &
FRONTEND_PID=$!
cd ..

# 启动 AI 服务
echo "🤖 启动 AI 服务 (FastAPI)..."
cd ai-service && uvicorn app.main:app --reload --port 8000 &
AI_PID=$!
cd ..

echo ""
echo "✅ DevFlow 开发环境已启动："
echo "   🌐 前端:     http://localhost:3000"
echo "   🤖 AI 服务:  http://localhost:8000"
echo "   📖 AI 文档:  http://localhost:8000/docs"
echo ""
echo "⚠️  Java 后端需要单独启动 (需要 Java 17+ 和 Maven)"
echo "   cd backend && mvn spring-boot:run"
echo ""
echo "按 Ctrl+C 停止所有服务"

# 等待
wait
