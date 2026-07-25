# Agent Demo

基于 Spring Boot、Spring AI、MyBatis、MySQL 和 Chroma 的本地知识库 RAG Agent 示例。

## 功能说明

项目的数据分工如下：

```text
MySQL / SQL 数据库
  - conversationId
  - 会话标题
  - 用户消息
  - 助手回复

Chroma 向量数据库
  - 本地知识文档分块
  - embedding 向量
  - 相似度检索

D:/agent/docs
  - 原始知识文档
  - 支持 .txt 和 .md
```

SQL 数据库和 Chroma 向量数据库互不冲突。SQL 只保存会话和消息，Chroma 只保存知识库向量。

## 环境要求

- JDK 21
- MySQL
- Python 3.10+
- Maven 或项目自带 Maven Wrapper

Docker 不是必需的。本项目提供了 Python 方式启动 Chroma。

## 配置密钥

启动前需要配置聊天模型和 embedding 模型的 API Key。

PowerShell 示例：

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DASHSCOPE_API_KEY="你的阿里云百炼 API Key"
```

也可以使用：

```powershell
$env:EMBEDDING_API_KEY="你的阿里云百炼 API Key"
```

当前默认配置：

```text
聊天模型：deepseek-chat
聊天接口：https://api.deepseek.com
Embedding 模型：text-embedding-v4
Embedding 接口：https://dashscope.aliyuncs.com/compatible-mode/v1
```

不要把真实 API Key 提交到 Git。`application-dev.yml` 已经在 `.gitignore` 中。

## 配置数据库

在 `src/main/resources/application-dev.yml` 中配置 MySQL：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/你的数据库名
    username: 你的用户名
    password: 你的密码
```

项目启动时会执行：

```text
src/main/resources/schema.sql
```

用于创建 `conversation` 和 `message` 表。

## 启动 Chroma

推荐使用 Python 脚本启动，不需要 Docker：

```powershell
cd D:\agent
.\scripts\start-chroma.ps1
```

首次运行会自动创建虚拟环境并安装 `chromadb`。后续启动会直接运行 Chroma。

Chroma 默认地址：

```text
http://localhost:8000
```

向量数据默认保存到：

```text
D:/agent/chroma-data
```

这个 PowerShell 窗口需要保持打开。关闭窗口后 Chroma 服务会停止。

如需升级 Chroma：

```powershell
.\scripts\start-chroma.ps1 -Upgrade
```

## 启动 Spring Boot

确保 Chroma 和 MySQL 都已经启动，然后运行 Spring Boot。

默认服务地址：

```text
http://localhost:8123/api
```

Knife4j / OpenAPI 页面可根据项目配置访问。

## 准备知识文档

把知识文档放到：

```text
D:/agent/docs
```

支持：

```text
.txt
.md
```

项目中已有测试文档：

```text
D:/agent/docs/rag-function-test.txt
```

## 文档入库

知识文档需要先分块、向量化并写入 Chroma。

Apifox 配置：

```text
Method: POST
URL: http://localhost:8123/api/rag/index
Body: 空
```

强制重建索引：

```text
Method: POST
URL: http://localhost:8123/api/rag/reindex
Body: 空
```

返回示例：

```json
{
  "addedChunks": 8,
  "deletedChunks": 0,
  "skippedDocs": 2
}
```

说明：

```text
addedChunks：新增入库的文档块数量
deletedChunks：删除的旧文档块数量
skippedDocs：未变化、跳过的文档数量
```

DashScope `text-embedding-v4` 单次 batch 不能超过 20，项目已经配置：

```yaml
agent:
  rag:
    batch-size: 20
```

## 测试检索

直接测试 Chroma 检索：

```text
Method: GET
URL: http://localhost:8123/api/rag/search?query=知识文档存在哪里？
```

如果返回中包含：

```text
D:\agent\docs\rag-function-test.txt
```

说明向量入库和检索成功。

## 测试聊天

聊天接口：

```text
Method: POST
URL: http://localhost:8123/api/chat
Headers:
  Content-Type: application/json
```

Body 示例：

```json
{
  "conversationId": null,
  "message": "知识文档存在哪里？",
  "confirm": false
}
```

测试问题：

```text
知识文档存在哪里？
向量数据存在哪里？
会话 ID 存在哪里？
当前测试文档的项目名称是什么？
使用的 embedding 模型是什么？
```

预期答案应能基于 `rag-function-test.txt` 回答，例如：

```text
原始知识文档保存在 D:/agent/docs。
```

## 常见问题

### 访问 `/api/rag/index` 返回 405

浏览器地址栏默认是 GET 请求，但入库接口是 POST。

请用 Apifox：

```text
POST http://localhost:8123/api/rag/index
```

### 访问 `/api/rag/reindex` 返回 404

说明运行中的 Spring Boot 不是最新代码。

请停止并重新启动 Spring Boot。

### Chroma 连接失败

确认 Chroma 窗口还在运行，并且看到：

```text
Connect to Chroma at: http://localhost:8000
```

再测试：

```text
GET http://localhost:8123/api/rag/search?query=知识文档存在哪里？
```

### DashScope 报 batch size is invalid

`text-embedding-v4` 单次最多 20 条输入。确认配置：

```yaml
agent:
  rag:
    batch-size: 20
```

### 普通聊天是否必须启动 Chroma

不是必须。

如果不使用 RAG，Chroma 没启动时项目也可以启动，但知识库检索不可用。

如果要使用 RAG，则必须启动 Chroma 并完成文档入库。

## 不要提交的本地文件

以下内容是本地运行产物，不应提交到 Git：

```text
.chroma-venv/
chroma-data/
docs/rag-manifest.json
docs/vector-store.json
src/main/resources/application-dev.yml
```
