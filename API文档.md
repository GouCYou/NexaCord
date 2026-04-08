# NexaCord API 文档

## 1. 认证 API

### 1.1 注册
- **URL**: `/api/auth/register`
- **方法**: `POST`
- **请求体**:
  ```json
  {
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }
  ```
- **响应**:
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "user": {
      "id": 1,
      "username": "testuser",
      "email": "test@example.com",
      "avatarUrl": null,
      "bannerUrl": null,
      "bio": null,
      "status": "online",
      "createdAt": "2026-01-03T12:00:00Z",
      "updatedAt": "2026-01-03T12:00:00Z"
    }
  }
  ```

### 1.2 登录
- **URL**: `/api/auth/login`
- **方法**: `POST`
- **请求体**:
  ```json
  {
    "usernameOrEmail": "testuser",
    "password": "password123"
  }
  ```
- **响应**: 与注册相同

### 1.3 刷新令牌
- **URL**: `/api/auth/refresh`
- **方法**: `POST`
- **请求体**:
  ```json
  {
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```
- **响应**: 与注册相同

## 2. 服务器 API

### 2.1 创建服务器
- **URL**: `/api/servers`
- **方法**: `POST`
- **认证**: 需要 Bearer Token
- **请求体**:
  ```json
  {
    "name": "My Server",
    "description": "A test server",
    "iconUrl": null,
    "bannerUrl": null
  }
  ```
- **响应**:
  ```json
  {
    "id": 1,
    "name": "My Server",
    "description": "A test server",
    "iconUrl": null,
    "bannerUrl": null,
    "createdAt": "2026-01-03T12:00:00Z",
    "updatedAt": "2026-01-03T12:00:00Z",
    "ownerId": 1
  }
  ```

### 2.2 获取服务器列表
- **URL**: `/api/servers`
- **方法**: `GET`
- **认证**: 需要 Bearer Token
- **响应**:
  ```json
  [
    {
      "id": 1,
      "name": "My Server",
      "description": "A test server",
      "iconUrl": null,
      "bannerUrl": null,
      "createdAt": "2026-01-03T12:00:00Z",
      "updatedAt": "2026-01-03T12:00:00Z",
      "ownerId": 1
    }
  ]
  ```

### 2.3 获取服务器详情
- **URL**: `/api/servers/{serverId}`
- **方法**: `GET`
- **认证**: 需要 Bearer Token
- **响应**: 与创建服务器相同

### 2.4 更新服务器
- **URL**: `/api/servers/{serverId}`
- **方法**: `PUT`
- **认证**: 需要 Bearer Token
- **请求体**: 与创建服务器相同
- **响应**: 与创建服务器相同

### 2.5 删除服务器
- **URL**: `/api/servers/{serverId}`
- **方法**: `DELETE`
- **认证**: 需要 Bearer Token
- **响应**: 204 No Content

## 3. 频道 API

### 3.1 创建频道
- **URL**: `/api/servers/{serverId}/channels`
- **方法**: `POST`
- **认证**: 需要 Bearer Token
- **请求体**:
  ```json
  {
    "name": "general",
    "description": "General channel",
    "type": "TEXT",
    "topic": "Welcome to the general channel!"
  }
  ```
- **响应**:
  ```json
  {
    "id": 1,
    "name": "general",
    "description": "General channel",
    "type": "TEXT",
    "topic": "Welcome to the general channel!",
    "createdAt": "2026-01-03T12:00:00Z",
    "updatedAt": "2026-01-03T12:00:00Z",
    "serverId": 1
  }
  ```

### 3.2 获取频道列表
- **URL**: `/api/servers/{serverId}/channels`
- **方法**: `GET`
- **认证**: 需要 Bearer Token
- **响应**:
  ```json
  [
    {
      "id": 1,
      "name": "general",
      "description": "General channel",
      "type": "TEXT",
      "topic": "Welcome to the general channel!",
      "createdAt": "2026-01-03T12:00:00Z",
      "updatedAt": "2026-01-03T12:00:00Z",
      "serverId": 1
    }
  ]
  ```

### 3.3 获取频道详情
- **URL**: `/api/channels/{channelId}`
- **方法**: `GET`
- **认证**: 需要 Bearer Token
- **响应**: 与创建频道相同

### 3.4 更新频道
- **URL**: `/api/channels/{channelId}`
- **方法**: `PUT`
- **认证**: 需要 Bearer Token
- **请求体**: 与创建频道相同
- **响应**: 与创建频道相同

### 3.5 删除频道
- **URL**: `/api/channels/{channelId}`
- **方法**: `DELETE`
- **认证**: 需要 Bearer Token
- **响应**: 204 No Content

## 4. 消息 API

### 4.1 创建消息
- **URL**: `/api/channels/{channelId}/messages`
- **方法**: `POST`
- **认证**: 需要 Bearer Token
- **请求体**:
  ```json
  {
    "content": "Hello, world!"
  }
  ```
- **响应**:
  ```json
  {
    "id": 1,
    "content": "Hello, world!",
    "createdAt": "2026-01-03T12:00:00Z",
    "updatedAt": "2026-01-03T12:00:00Z",
    "author": {
      "id": 1,
      "username": "testuser",
      "email": "test@example.com",
      "avatarUrl": null,
      "status": "online"
    },
    "channel": {
      "id": 1,
      "name": "general"
    },
    "attachments": [],
    "isEdited": false,
    "isDeleted": false
  }
  ```

### 4.2 获取消息列表
- **URL**: `/api/channels/{channelId}/messages`
- **方法**: `GET`
- **认证**: 需要 Bearer Token
- **参数**:
  - `sort`: 排序方式 (`asc` 或 `desc`，默认 `desc`)
- **响应**:
  ```json
  [
    {
      "id": 1,
      "content": "Hello, world!",
      "createdAt": "2026-01-03T12:00:00Z",
      "updatedAt": "2026-01-03T12:00:00Z",
      "author": {
        "id": 1,
        "username": "testuser",
        "email": "test@example.com",
        "avatarUrl": null,
        "status": "online"
      },
      "channel": {
        "id": 1,
        "name": "general"
      },
      "attachments": [],
      "isEdited": false,
      "isDeleted": false
    }
  ]
  ```

### 4.3 更新消息
- **URL**: `/api/messages/{messageId}`
- **方法**: `PUT`
- **认证**: 需要 Bearer Token
- **请求体**:
  ```json
  {
    "content": "Hello, updated world!"
  }
  ```
- **响应**: 与创建消息相同

### 4.4 删除消息
- **URL**: `/api/messages/{messageId}`
- **方法**: `DELETE`
- **认证**: 需要 Bearer Token
- **响应**: 204 No Content

## 5. 文件 API

### 5.1 上传文件
- **URL**: `/api/files/upload`
- **方法**: `POST`
- **认证**: 需要 Bearer Token
- **请求体**: `multipart/form-data`
  - `file`: 要上传的文件
- **响应**:
  ```json
  "https://nexacord-rustfs.example.com/files/1234567890abcdef.jpg"
  ```

### 5.2 上传多个文件
- **URL**: `/api/files/upload-multiple`
- **方法**: `POST`
- **认证**: 需要 Bearer Token
- **请求体**: `multipart/form-data`
  - `files`: 要上传的多个文件
- **响应**:
  ```json
  [
    "https://nexacord-rustfs.example.com/files/1234567890abcdef.jpg",
    "https://nexacord-rustfs.example.com/files/abcdef1234567890.png"
  ]
  ```

### 5.3 删除文件
- **URL**: `/api/files/delete`
- **方法**: `DELETE`
- **认证**: 需要 Bearer Token
- **参数**:
  - `fileUrl`: 要删除的文件URL
- **响应**: 204 No Content

### 5.4 生成预签名URL
- **URL**: `/api/files/presigned-url`
- **方法**: `GET`
- **认证**: 需要 Bearer Token
- **参数**:
  - `fileUrl`: 文件URL
  - `expirationMinutes`: 过期时间（分钟，默认15）
- **响应**:
  ```json
  "https://nexacord-rustfs.example.com/files/1234567890abcdef.jpg?AWSAccessKeyId=...&Expires=...&Signature=..."
  ```

## 6. WebSocket API

### 6.1 连接
- **URL**: `ws://localhost:8080/ws`
- **认证**: 需要在连接头中提供 `Authorization: Bearer <token>`

### 6.2 发送消息
- **目的地**: `/app/channel/{channelId}/send-message`
- **消息格式**:
  ```json
  {
    "content": "Hello, WebSocket!",
    "channelId": 1
  }
  ```

### 6.3 接收消息
- **订阅**: `/topic/channel/{channelId}`
- **消息格式**:
  ```json
  {
    "id": 1,
    "content": "Hello, WebSocket!",
    "timestamp": "2026-01-03T12:00:00Z",
    "type": "CHAT",
    "author": {
      "id": 1,
      "username": "testuser",
      "avatarUrl": null,
      "status": "online"
    },
    "channelId": 1
  }
  ```

### 6.4 输入状态
- **目的地**: `/app/channel/{channelId}/typing`
- **消息格式**: 不需要消息体
- **订阅**: `/topic/channel/{channelId}/typing`
- **响应格式**:
  ```json
  {
    "type": "TYPING",
    "author": {
      "id": 1,
      "username": "testuser"
    },
    "channelId": 1
  }
  ```

### 6.5 停止输入
- **目的地**: `/app/channel/{channelId}/stop-typing`
- **消息格式**: 不需要消息体
- **订阅**: `/topic/channel/{channelId}/typing`
- **响应格式**:
  ```json
  {
    "type": "STOP_TYPING",
    "author": {
      "id": 1,
      "username": "testuser"
    },
    "channelId": 1
  }
  ```

## 7. 测试方式

### 7.1 使用 Postman
1. 导入API文档到Postman
2. 设置环境变量 `baseUrl` 为 `http://localhost:8080`
3. 使用注册或登录接口获取令牌
4. 在Postman中设置Bearer令牌认证
5. 测试各个API端点

### 7.2 使用 curl
```bash
# 注册
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"testuser","email":"test@example.com","password":"password123"}'

# 登录
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"usernameOrEmail":"testuser","password":"password123"}'

# 使用令牌访问受保护的资源
curl -X GET http://localhost:8080/api/servers -H "Authorization: Bearer <token>"
```

### 7.3 使用 SpringDoc OpenAPI
1. 启动应用程序
2. 访问 `http://localhost:8080/swagger-ui.html`
3. 在Swagger UI中测试各个API端点

## 8. 错误处理

### 8.1 常见错误响应
```json
{
  "timestamp": "2026-01-03T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Resource not found",
  "path": "/api/servers/999"
}
```

### 8.2 状态码
- 200 OK: 请求成功
- 201 Created: 资源创建成功
- 204 No Content: 请求成功但无响应体
- 400 Bad Request: 请求参数错误
- 401 Unauthorized: 未认证
- 403 Forbidden: 无权限访问
- 404 Not Found: 资源不存在
- 500 Internal Server Error: 服务器内部错误

## 9. 认证与授权

### 9.1 JWT 令牌
- 所有受保护的API都需要在请求头中提供 `Authorization: Bearer <token>`
- 令牌有效期为1小时，刷新令牌有效期为7天
- 使用 `/api/auth/refresh` 端点刷新令牌

### 9.2 角色与权限
- `OWNER`: 服务器所有者，拥有所有权限
- `ADMIN`: 服务器管理员，可管理服务器和频道
- `MEMBER`: 普通成员，可发送消息和参与讨论

## 10. 性能与限制

### 10.1 速率限制
- API请求限制：60次/分钟/IP
- WebSocket连接限制：100个/IP

### 10.2 文件上传限制
- 单个文件大小限制：100MB
- 单次上传文件数量限制：10个
- 总存储空间限制：10GB/用户

## 11. 监控与日志

### 11.1 日志级别
- ERROR: 错误信息
- WARN: 警告信息
- INFO: 普通信息
- DEBUG: 调试信息

### 11.2 监控指标
- 请求响应时间
- 错误率
- 并发连接数
- 内存使用情况
- CPU使用率

## 12. 部署

### 12.1 环境要求
- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- RustFS/S3兼容对象存储

### 12.2 配置文件
```properties
# 服务器配置
server.port=8080

# 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/nexacord?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=123456

# Redis配置
spring.data.redis.host=localhost
spring.data.redis.port=6379

# JWT配置
jwt.secret=your-secret-key-change-this-in-production
jwt.expiration=3600000
jwt.refresh-token.expiration=604800000

# RustFS/S3配置
rustfs.endpoint=http://localhost:9000
rustfs.access-key=your-access-key
rustfs.secret-key=your-secret-key
rustfs.bucket=nexacord
rustfs.region=us-east-1
rustfs.path-style-access-enabled=true
```

## 13. 版本控制

### 13.1 API 版本
- 当前版本: v1
- 版本控制: 通过URL路径（例如 `/api/v1/servers`）

### 13.2 变更日志
- v1.0.0: 初始版本，包含所有核心功能
- v1.1.0: 添加文件上传功能
- v1.2.0: 改进WebSocket性能

## 14. 联系方式

### 14.1 支持
- 邮箱: support@nexacord.com
- 文档: https://docs.nexacord.com
- GitHub: https://github.com/nexacord/nexacord-server

### 14.2 贡献
- 提交Issue: https://github.com/nexacord/nexacord-server/issues
- 提交PR: https://github.com/nexacord/nexacord-server/pulls

## 15. 许可证

### 15.1 开源协议
- MIT License
- 详见: https://github.com/nexacord/nexacord-server/blob/main/LICENSE
