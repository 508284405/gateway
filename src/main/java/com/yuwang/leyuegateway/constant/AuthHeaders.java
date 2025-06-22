package com.yuwang.leyuegateway.constant;

/**
 * 认证相关Header常量
 *
 * @author Trae
 * @since 2024-01-20
 */
public final class AuthHeaders {
    
    /**
     * 用户ID Header
     */
    public static final String USER_ID = "X-User-Id";
    
    /**
     * 用户名 Header
     */
    public static final String USERNAME = "X-Username";
    
    /**
     * 用户角色 Header
     */
    public static final String USER_ROLES = "X-User-Roles";
    
    /**
     * 用户菜单权限 Header
     */
    public static final String USER_MENUS = "X-User-Menus";
    
    private AuthHeaders() {
        // 私有构造函数，防止实例化
    }
} 