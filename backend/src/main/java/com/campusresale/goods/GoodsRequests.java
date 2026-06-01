package com.campusresale.goods;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class GoodsRequests {

    private GoodsRequests() {
    }

    public record UpsertRequest(
            @Size(max = 80, message = "标题不能超过 80 个字符")
            String title,

            @Size(max = 2000, message = "描述不能超过 2000 个字符")
            String description,

            Long categoryId,

            String conditionLevel,

            BigDecimal listPrice,

            Long tradePlaceId,

            @Size(max = 255, message = "交易地点补充不能超过 255 个字符")
            String tradePlaceDetail,

            @Size(max = 255, message = "可交易时间不能超过 255 个字符")
            String availableTimeText,

            List<Long> imageFileIds,

            List<Long> tagIds
    ) {
    }

    public record ReviewRequest(
            @Size(max = 500, message = "审核理由不能超过 500 个字符")
            String reason
    ) {
    }
}
