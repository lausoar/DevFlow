# 集成验证（CI）

> `scripts/verify-integration.sh` + `.github/workflows/ci.yml` 的 `integration` job。
> 目的：自动抓住**单元测试覆盖不到**的一类缺陷。
> 背景见 [E2E_VERIFICATION_2026-11-15.md](./E2E_VERIFICATION_2026-11-15.md)。

## 为什么需要它

第一次端到端验证发现的 5 个缺陷，**没有一个能被编译、类型检查或单元测试发现**：

| 缺陷 | 为什么单测抓不到 |
|------|------------------|
| 第 2/3 批的新表从未被创建 | 编译通过、单测通过，接口运行到 SQL 时才 500 |
| `schema.sql` 非幂等 | 只有"在干净库上重复执行"才暴露 |
| 存量状态值未归一 | 只有真实历史数据才暴露 |
| 审计哈希链三处错误 | 只有真实写入再读回才暴露 |
| CSV 导出 Servlet 流冲突 | 只有真发一次 HTTP 请求才暴露 |

结论不是"测试补得不够"，而是**集成层的正确性需要另一层验证手段**。

## 它做什么

`scripts/verify-integration.sh` 在独立数据库 `devflow_ci` 上依次执行：

1. **重建库** → 首次执行 `schema.sql`，检查除已知重复列/键外无其它错误；
2. **检查 15 张新表**真的存在（回归"表从未创建"）；
3. **再执行一次 schema** 验证幂等，并确认 7 个幂等索引都建成；
4. **启动后端**（真实 MySQL，不依赖 Redis/MinIO/AI），等健康探针 UP；
5. **打一遍关键接口**：登录、401、企业/项目、工作流模板与状态流转、
   版本创建与重复、部署审批与自审拦截、DORA、测试计划与轮次、
   结构化查询注入、审计链正向 + 篡改检测、CSV 导出、双因素绑定、
   管理端口与业务端口隔离；
6. 汇总通过/失败，任一项失败即非零退出。

共 **29 项检查**。

## 本地运行

```bash
DB_HOST=127.0.0.1 DB_PASSWORD=devflow123 bash scripts/verify-integration.sh
```

需要：bash、mysql 客户端、JDK 17、Maven。脚本会自己起后端（默认端口 18080 / 管理端口 19090），
结束后自动停掉并清理临时目录。**只使用 `devflow_ci` 库**，不碰业务库。

## CI 中运行

`.github/workflows/ci.yml` 的 `integration` job 用 `services: mysql:8.0` 起一个真实数据库，
安装 mysql 客户端后直接跑同一个脚本。镜像构建 job 依赖它（先验证再打包）。

## 本次固化过程中又发现两个真实缺陷

写脚本的过程中，脚本自己抓到了两个新问题——恰好证明这类检查有效：

### 1. 应用数据被写进了错误的库（严重）

`schema.sql` 开头有 `CREATE DATABASE devflow; USE devflow;`。
Spring 启动时用连接池里的一条连接执行这个脚本，`USE devflow` **把那条连接的默认库切走了**。
连接归还连接池后，后续业务查询复用同一条连接 → **即使 JDBC URL 指向别的库，数据仍写进 `devflow`**。

后果：任何"用另一个库跑测试/验证"的做法都会静默污染主库。验证时我就亲眼看到
`devflow_ci` 是空的、数据全进了 `devflow`。

修复：删除 `schema.sql` 里的 `CREATE DATABASE` / `USE`，库名由 JDBC URL 或
docker 的 `MYSQL_DATABASE` 决定。`docker-compose.yml` 与 `docker-compose.prod.yml`
本来就设了 `MYSQL_DATABASE: devflow`，所以部署行为不变。

### 2. Prometheus 指标端点不可达

`SecurityConfig` 只放行了 `/actuator/health`，而 `management.endpoints.web.exposure.include`
还导出了 `prometheus`。结果：监控系统抓 `/actuator/prometheus` 会拿到 401，指标形同虚设。

修复：改为放行 `/actuator/**`。这不会削弱业务端口的安全性——管理端点在**独立端口**
（`management.server.port`，默认 9090）注册，业务端口上根本不存在这些路径，
脚本里也有一条检查专门验证这一点。

## 已知边界

- 脚本验证的是**后端 + 数据库**这条链路；前端只验证到 `npm run build` + `tsc`。
- 不覆盖多实例场景（限流计数、TOTP 凭证、审计链头都是进程内状态）。
- 不覆盖 SSO/SCIM、外部 IdP、真实 LLM 调用。
- `ALTER TABLE ADD COLUMN` 仍非幂等，靠项目的 `continue-on-error` 约定；
  脚本把"重复列/重复键"之外的错误判定为失败，能在一定程度上兜住这类问题。
