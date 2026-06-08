// 文件功能：定义平台支持的文件业务用途，上传接口按用途决定默认可见范围和后续校验规则。
package com.campusresale.files;

import com.campusresale.platform.api.ApiExceptions;
import java.util.Arrays;
import java.util.Map;

public enum FileKind {
    AVATAR,
    GOODS_IMAGE,
    /** 校园认证材料属于敏感证件图片，只允许本人看脱敏预览、管理员审原件。 */
    CAMPUS_AUTH_MATERIAL,
    ORDER_EVIDENCE,
    REPORT_EVIDENCE,
    APPEAL_EVIDENCE,
    /** 私信图片按会话参与者授权，不简单按公开/私有处理。 */
    MESSAGE_IMAGE;

    public static FileKind parse(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiExceptions.validation("文件用途不支持", Map.of("field", "fileKind")));
    }
}
