# files 目录说明

本目录是 B 成员负责的文件 API 与文件元数据服务。

接口：

- `POST /api/files`
- `GET /api/files/{id}`
- `GET /api/files/{id}/content`

关键口径：

- 文件二进制进入 MinIO，数据库 `stored_files` 只保存元数据和对象存储 key。
- `CAMPUS_AUTH_MATERIAL` 强制为 `ADMIN_ONLY`，数据库和应用层都会约束。
- 认证材料本人只能读取元数据和脱敏预览，不能读取原始证件图片。
- 管理员读取认证材料原件时必须写入 `sensitive_access_logs`。
- M1 只允许 JPEG、PNG、WebP，并使用声明 `Content-Type` 加图片魔数共同校验。
