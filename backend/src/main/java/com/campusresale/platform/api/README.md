# platform/api 文件说明

本目录放跨模块统一 API 错误处理能力。

| 文件 | 说明 |
| --- | --- |
| `ApiErrorResponse.java` | 统一错误响应结构。 |
| `ApiException.java` | 业务异常对象，携带 HTTP 状态、错误码、详情。 |
| `ApiExceptions.java` | 常用业务异常工厂，集中维护错误码和中文提示。 |
| `GlobalApiExceptionHandler.java` | 全局异常处理器，把异常转换成统一 JSON。 |

扩展提示：

- 新业务不要直接返回零散错误格式。
- 需要新增错误码时，先对齐 `docs/历史资料/阶段契约/m1_contracts.md`，再补 `ApiExceptions`。
