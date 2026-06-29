# NexaCord

<p align="center">
  🇨🇳 简体中文 ·
  <a href="i18n/README.zh-TW.md">🇭🇰 繁體中文</a> ·
  <a href="i18n/README.en-US.md">🇺🇸 English</a>
</p>

NexaCord 是一个面向实时社区交流场景的全栈聊天平台，提供接近现代社区工具的服务器、频道、好友、私信和语音空间体验。
项目包含 Vue 3 网页端和 Spring Boot 后端，覆盖账号注册登录、邮箱验证码、JWT 会话、服务器和频道管理、文字消息、图片附件、好友关系、私信会话、语音频道、邀请链接和成员角色等功能。

## 目录

- [演示截图](#演示截图)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [开发检查](#开发检查)
- [安全说明](#安全说明)
- [贡献](#贡献)
- [许可证](#许可证)

## 演示截图

<p align="center">
  <span>
    <img width="19%" alt="账号登录" src="docs/screenshots/account-login.png" />
  </span>
  <span>
    <img width="19%" alt="账号注册" src="docs/screenshots/account-register.png" />
  </span>
  <span>
    <img width="19%" alt="好友主页" src="docs/screenshots/friends-home.png" />
  </span>
  <span>
    <img width="19%" alt="频道聊天" src="docs/screenshots/channel-chat.png" />
  </span>
  <span>
    <img width="19%" alt="语音频道" src="docs/screenshots/voice-channel.png" />
  </span>
  <br />
  <sub>账号登录</sub>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <sub>账号注册</sub>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <sub>好友主页</sub>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <sub>频道聊天</sub>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <sub>语音频道</sub>
</p>

## 核心功能

- 账号体系：支持注册、登录、邮箱验证码、密码重置、密码强度校验和本地会话恢复。
- 会话安全：使用 JWT 与刷新令牌维护登录状态，并支持账号在其他设备登录后的会话替换提示。
- 服务器空间：支持创建服务器、切换服务器、维护服务器资料、邀请成员和管理成员角色。
- 频道组织：支持文字频道、语音频道和分类频道，频道列表会随服务器切换实时刷新。
- 文字消息：支持频道消息加载、发送、编辑、删除、表情输入和未读状态同步。
- 文件附件：支持图片选择、上传、预览和多附件发送，后端可接入七牛云对象存储。
- 好友与私信：支持好友请求、好友筛选、私聊会话、私信消息和好友在线状态展示。
- 语音频道：支持加入/离开语音频道、麦克风控制、远端声音控制和语音成员状态显示。
- 实时通信：使用 STOMP over SockJS 推送消息、频道、服务器、好友状态和会话事件。
- 接口文档：后端集成 Springdoc OpenAPI，可在本地查看 Swagger UI。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA |
| 数据 | MySQL, Redis, Hibernate |
| 实时通信 | Spring WebSocket, STOMP, SockJS |
| 鉴权 | JWT, Refresh Token, Spring Security Filter |
| 文件存储 | 七牛云对象存储 |
| 邮件服务 | SMTP 邮箱验证码 |
| 前端 | Vue 3, TypeScript, Vite, Vue Router, Pinia |
| UI | Element Plus, Lucide Vue, 自定义 CSS |
| HTTP | Axios |

## 项目结构

```text
NexaCord/
├── nexacord-server/              # Spring Boot 后端服务
├── nexacord-web/                 # Vue 3 网页端
├── docs/screenshots/             # README 演示截图
├── i18n/                         # 多语言 README
├── nexacord_server.sql           # 服务端数据库结构和可选初始化数据
└── README.md
```

## 环境要求

- JDK 17+
- Maven 3.9+，或使用 `nexacord-server` 内置 Maven Wrapper
- MySQL 8+
- Redis 6+
- Node.js 20+
- 可选：七牛云对象存储、SMTP 邮箱服务

## 快速开始

### 1. 创建数据库

```sql
CREATE DATABASE nexacord
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

如需导入初始化表结构和示例数据：

```bash
mysql -u root -p nexacord < nexacord_server.sql
```

### 2. 配置后端私有参数

后端默认会尝试读取 `nexacord-server/application-local.yml`。建议把本地数据库、Redis、邮箱、JWT 和对象存储参数放在这个文件里，不要提交到仓库。

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/nexacord?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: your_mysql_user
    password: your_mysql_password
  data:
    redis:
      host: localhost
      port: 6379
  mail:
    username: your_mail_account
    password: your_mail_authorization_code

jwt:
  secret: replace-with-a-long-random-base64-secret

qiniu:
  access-key: your_qiniu_access_key
  secret-key: your_qiniu_secret_key
  bucket: your_bucket
  domain: your_cdn_domain

app:
  cors:
    allowed-origin-patterns: http://localhost:5173,http://127.0.0.1:5173
```

### 3. 启动后端

```bash
cd nexacord-server
./mvnw spring-boot:run
```

Windows：

```powershell
cd nexacord-server
.\mvnw.cmd spring-boot:run
```

默认后端地址：`http://localhost:8080`

接口文档：`http://localhost:8080/swagger-ui/index.html`

### 4. 启动前端

在 `nexacord-web/.env.local` 中指向本地后端：

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_BASE_URL=http://localhost:8080
```

启动网页端：

```bash
cd nexacord-web
npm install
npm run dev
```

默认 Vite 地址通常为 `http://localhost:5173`。

## 配置说明

- `nexacord-server/src/main/resources/application.yml` 保留默认开发配置，并通过环境变量支持覆盖。
- `nexacord-server/application-local.yml` 适合放置本机私有配置。
- 前端本地开发必须设置 `VITE_API_BASE_URL`，否则会使用代码中的默认远程 API 地址。
- `VITE_WS_BASE_URL` 应指向后端根地址，不带 `/api`。
- 生产环境建议使用环境变量或独立部署配置管理数据库密码、JWT 密钥、邮箱授权码和对象存储密钥。

## 开发检查

后端测试：

```bash
cd nexacord-server
./mvnw test
```

后端编译：

```bash
cd nexacord-server
./mvnw -DskipTests compile
```

前端构建：

```bash
cd nexacord-web
npm run build
```

## 安全说明

- 不要提交数据库密码、邮箱授权码、JWT 密钥、七牛云 Access Key 或 Secret Key。
- `application.yml` 中的默认 JWT 密钥只能作为开发兜底，生产环境必须通过 `JWT_SECRET` 或私有配置覆盖。
- 如果任何密钥已经进入公开仓库，应立即吊销并重新生成。
- 上传文件建议使用对象存储和 CDN 域名，生产环境不要长期依赖本地文件服务。
- CORS 只应放行可信前端域名，避免在生产环境使用过宽的来源匹配。

## 贡献

欢迎提交 Issue 和 Pull Request。建议在提交前说明：

- 问题背景或功能目标
- 影响范围
- 主要实现思路
- 已完成的测试或验证步骤

## 许可证

当前仓库尚未声明开源许可证。若需要对外分发或接受贡献，请先补充 `LICENSE` 文件。
