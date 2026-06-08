# identity/verification 目录说明

本目录是 B 成员负责的校园认证能力。

接口：

- `GET /api/verifications/me`
- `PUT /api/verifications/me`
- `POST /api/verifications/me/submit`
- `GET /api/admin/verifications`
- `POST /api/admin/verifications/{id}/approve`
- `POST /api/admin/verifications/{id}/reject`

关键口径：

- `PUT /api/verifications/me` 只保存草稿或积累状态，`POST /submit` 才进入 `PENDING_REVIEW`。
- 提交审核必须已有学生证或校园卡材料。
- 校园邮箱 M1 只校验 `system_configs.campus.auth.email_suffixes` 后缀。
- 审核通过后满足完整规则才授予 `VERIFIED_STUDENT`。
- `CurrentUser.canTrade` 使用完整规则：分数达到 60、证件因子通过、认证状态 `APPROVED`、拥有 `VERIFIED_STUDENT` 角色。
- M1 不允许已 `APPROVED` 认证被原地重提并降级；后续如需重新认证，应新增变更申请或版本模型。
