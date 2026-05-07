-- Nexacord 服务端数据库结构
-- 本脚本用于创建服务端所需的数据表

SET FOREIGN_KEY_CHECKS = 0;

-- 如果表已存在则先删除
DROP TABLE IF EXISTS attachments;
DROP TABLE IF EXISTS server_invites;
DROP TABLE IF EXISTS direct_messages;
DROP TABLE IF EXISTS direct_conversations;
DROP TABLE IF EXISTS friendships;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS channels;
DROP TABLE IF EXISTS members;
DROP TABLE IF EXISTS servers;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- 创建用户表
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(255),
    banner_url VARCHAR(255),
    banner_color VARCHAR(20),
    bio VARCHAR(500),
    status VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建服务器表
CREATE TABLE servers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    icon_url VARCHAR(255),
    banner_url VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建频道表
CREATE TABLE channels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type ENUM('TEXT', 'VOICE', 'CATEGORY') NOT NULL DEFAULT 'TEXT',
    topic VARCHAR(1000),
    nsfw BOOLEAN NOT NULL DEFAULT FALSE,
    parent_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    server_id BIGINT NOT NULL,
    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES channels(id) ON DELETE SET NULL
);

-- 创建消息表
CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    author_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (channel_id) REFERENCES channels(id) ON DELETE CASCADE
);

-- 创建成员表
CREATE TABLE members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    server_id BIGINT NOT NULL,
    nickname VARCHAR(255),
    avatar_url VARCHAR(255),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    role ENUM('OWNER', 'ADMIN', 'MODERATOR', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_server (user_id, server_id)
);

-- 创建好友关系表
CREATE TABLE friendships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    addressee_id BIGINT NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'BLOCKED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (addressee_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 创建私聊会话表
CREATE TABLE direct_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_one_id BIGINT NOT NULL,
    user_two_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_one_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (user_two_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_direct_conversation_users (user_one_id, user_two_id)
);

-- 创建私聊消息表
CREATE TABLE direct_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT,
    conversation_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (conversation_id) REFERENCES direct_conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 创建服务器邀请链接表
CREATE TABLE server_invites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    server_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    expires_at TIMESTAMP NULL,
    max_uses INT NULL,
    use_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
    FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 创建附件表
CREATE TABLE attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    url VARCHAR(255) NOT NULL,
    message_id BIGINT NOT NULL,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);

-- 创建索引以提升查询性能
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_servers_name ON servers(name);
CREATE INDEX idx_channels_server_id ON channels(server_id);
CREATE INDEX idx_messages_channel_id ON messages(channel_id);
CREATE INDEX idx_messages_author_id ON messages(author_id);
CREATE INDEX idx_members_user_id ON members(user_id);
CREATE INDEX idx_members_server_id ON members(server_id);
CREATE INDEX idx_friendships_requester ON friendships(requester_id);
CREATE INDEX idx_friendships_addressee ON friendships(addressee_id);
CREATE INDEX idx_direct_conversations_user_one ON direct_conversations(user_one_id);
CREATE INDEX idx_direct_conversations_user_two ON direct_conversations(user_two_id);
CREATE INDEX idx_direct_messages_conversation ON direct_messages(conversation_id);
CREATE INDEX idx_direct_messages_author ON direct_messages(author_id);
CREATE INDEX idx_server_invites_code ON server_invites(code);
CREATE INDEX idx_attachments_message_id ON attachments(message_id);

-- 写入可选的初始化数据
INSERT INTO users (username, display_name, email, password, status) VALUES
('admin', 'admin', 'admin@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBQVJsCn6JmZ9U.r8HI563Ux9B87xUu', 'online');

-- 为管理员创建默认服务器
INSERT INTO servers (name, description) VALUES
('管理员服务器', '管理员用户的默认服务器');

-- 将管理员设置为服务器拥有者
INSERT INTO members (user_id, server_id, role) VALUES
(1, 1, 'OWNER');

-- 为默认服务器创建默认频道
INSERT INTO channels (name, type, topic, server_id) VALUES
('综合讨论', 'TEXT', '欢迎来到综合讨论频道！', 1),
('语音大厅', 'VOICE', '随时加入语音交流。', 1);

-- 当前结构支持以下能力：
-- 1. 用户认证和用户资料
-- 2. 服务器创建和管理
-- 3. 服务器内频道组织
-- 4. 消息发送和接收
-- 5. 文件附件
-- 6. 成员角色和基础权限
-- 7. 好友私聊

-- 更多接口信息可参考接口文档。
