// 文件功能：定义用户自助注销接口的响应结构。
package com.campusresale.identity.api;

/**
 * 自助注销响应；成功后账号被软禁用，当前和其他 session 均被撤销。
 */
public record DeleteAccountResponse(
        /** 是否完成注销。 */
        boolean deleted
) {
}
