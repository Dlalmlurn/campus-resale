// 文件功能：定义文件读取可见范围，服务层据此决定元数据和原件内容的授权方式。
package com.campusresale.files;

import com.campusresale.platform.api.ApiExceptions;
import java.util.Arrays;
import java.util.Map;

public enum VisibilityScope {
    PUBLIC,
    PRIVATE,
    /** 仅业务参与者可见，例如买卖双方所在会话里的私信图片。 */
    PARTICIPANTS,
    /** 管理员专属原件范围，例如校园认证材料；本人侧通常只返回脱敏预览。 */
    ADMIN_ONLY;

    public static VisibilityScope parseOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(scope -> scope.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiExceptions.validation("文件可见范围不支持", Map.of("field", "visibilityScope")));
    }
}
