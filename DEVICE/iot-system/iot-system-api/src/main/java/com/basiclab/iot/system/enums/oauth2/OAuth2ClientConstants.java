package com.basiclab.iot.system.enums.oauth2;

/**
 * OAuth2.0 客户端的通用枚举
 *
 * @author reese
 * @email reese
 */
public interface OAuth2ClientConstants {

    String CLIENT_ID_DEFAULT = "default";

    /** 30 天免登录：access / refresh 均为 30 天 */
    int REMEMBER_ME_ACCESS_TOKEN_VALIDITY_SECONDS = 30 * 24 * 60 * 60;

    int REMEMBER_ME_REFRESH_TOKEN_VALIDITY_SECONDS = 30 * 24 * 60 * 60;

    /** 普通登录：refresh token 1 天 */
    int DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS = 24 * 60 * 60;

}
