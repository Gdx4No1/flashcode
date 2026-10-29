# Flashcode

葛佳维护的 AI 应用生成与管理学习项目，基于课程框架继续实践需求整理、代码生成、构建预览、源码编辑和应用管理。

## 项目结构

```text
flashcode/
├─ backend/                 Java 21 / Spring Boot 多模块后端
│  ├─ flashcode-admin/      用户与后台服务
│  ├─ flashcode-common/     公共组件
│  ├─ flashcode-file/       文件服务
│  ├─ flashcode-gateway/    网关
│  ├─ flashcode-portal/     应用生成服务
│  ├─ flashcode-gitee-mcp-server/
│  ├─ flashcode-mstemplate/
│  ├─ deploy/manual/       供人工审阅的 SQL / RAG 配置参考
│  └─ tools/delivery/      本地构建及源码整理工具
└─ frontend/                Vue 3 / Vite / Element Plus 前端
```

## 环境

- Java 21、Maven 3.9.x。
- Node.js `^20.19.0 || >=22.12.0`、npm，使用 package-lock.json。
- MySQL 8.4.2、Redis 7.0.15、RabbitMQ 3.12.6、Nacos 2.2.2。
- 完整 RAG 需要 Milvus 2.6.4、配套 etcd/MinIO、可调用的 embedding 服务和一致的向量维度。
- 相关功能需要本人文本/视觉模型 API、OSS、Gitee、预览/构建环境等配置。在线编辑还需要 code-server。

源码不包含真实 API Key、数据库口令、Docker 私钥、现用 Nacos 配置或本地运行数据。旧开发机部署文件含私有参数，未上传；这里的 SQL 和 RAG 配置为参考，不是已部署的完整环境。

## 构建与开发

Windows 本地工作目录、构建缓存和输出使用 D 盘。PowerShell 中先为当前进程设置 Java 21，工具路径可通过下列脚本参数指定：

```powershell
cd backend
./tools/delivery/build.ps1
```

该脚本会执行后端测试与打包、前端依赖安装及构建，默认跳过 Docker 部署；不会配置中间件或迁移数据库。默认通过 Maven Central 解析依赖，实际构建受网络与仓库可用性影响。

单独开发前端：

```powershell
cd frontend
# 从 .env.example 复制得到 .env.local，填写自己的 API/预览地址。
npm ci
npm run dev
```

禁止把模型密钥放在 VITE_ 环境变量中；这些变量会进入浏览器。JWT 使用 `FLASHCODE_JWT_SECRET`，必须为至少 64 个安全随机字节的 Base64；四个相关后端进程需要共享同一私有值，不提供固定默认密钥。构建脚本只为本机生成不入库的私有测试值。

本仓库尚未提供经过完整验收的一键启动；原开发机的重启脚本、SSH 工具、运行凭据及历史缓存未上传。配置环境前不要直接执行 Maven deploy，它可能触发远端 Docker 操作。启动 Portal 还会按原实现向向量库写入示例文档，需确认目标实例和权限。

## 已知验证状态

2026-10-30 本地记录：后端 23 模块编译成功；前端生产构建成功；独立关于页桌面/手机视觉检查通过。完整启动仍缺少可用的 embedding/Milvus/基础设施连接。

原 common-core 存在两个 null Long 复制到 long 的测试错误，Gitee MCP 模块测试依赖不齐；未通过跳过测试来声称全测成功。历史构建成功不表示这些单元测试已修复。本次源码上传的最新验证见 [docs/发布检查.md](docs/发布检查.md)。

## 来源和使用条件

后端课程来源：[zhibite-edu/flashcode](https://gitee.com/zhibite-edu/flashcode)；前端课程来源：[zhibite-edu/flashcode-fe](https://gitee.com/zhibite-edu/flashcode-fe)。原权利归属和使用条件分别保留在 [后端来源说明](backend/NOTICE.upstream.md) 与 [前端来源说明](frontend/NOTICE.upstream.md)。本仓库不另行授予与原条件冲突的开源许可，也不声称课程原代码为本人原创。使用、改编或再分发应取得相应权利。
