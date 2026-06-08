# platform/storage 目录说明

本目录放跨模块复用的对象存储适配能力。

当前实现：

- `ObjectStorageClient` 是业务层依赖的接口。
- `MinioObjectStorageClient` 使用 MinIO Java SDK 访问 Docker Compose 中的 MinIO。
- bucket 在首次写入对象时按需创建。

约定：

- 业务模块不要直接依赖 MinIO SDK。
- 文件访问权限由 `files` 模块控制，对象存储层只负责读写对象。
