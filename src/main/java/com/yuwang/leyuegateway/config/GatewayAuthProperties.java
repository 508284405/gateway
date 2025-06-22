package com.yuwang.leyuegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网关认证配置属性
 *
 * @author Trae
 * @since 2024-01-20
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class GatewayAuthProperties {
    
    /**
     * RSA 公钥（用于验签）
     */
    private String publicKey;
    
    /**
     * 白名单路径列表（Ant表达式）
     */
    private List<String> whitelist;
    
    /**
     * 时间漂移容忍度（秒）
     */
    private long clockSkew = 60;
    
    /**
     * 是否启用菜单权限验证
     */
    private boolean enableMenuPermission = true;
    
    /**
     * 菜单权限验证白名单（不需要菜单权限验证的路径）
     */
    private List<String> menuPermissionWhitelist;

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public long getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(long clockSkew) {
        this.clockSkew = clockSkew;
    }

    public boolean isEnableMenuPermission() {
        return enableMenuPermission;
    }

    public void setEnableMenuPermission(boolean enableMenuPermission) {
        this.enableMenuPermission = enableMenuPermission;
    }

    public List<String> getMenuPermissionWhitelist() {
        return menuPermissionWhitelist;
    }

    public void setMenuPermissionWhitelist(List<String> menuPermissionWhitelist) {
        this.menuPermissionWhitelist = menuPermissionWhitelist;
    }
} 