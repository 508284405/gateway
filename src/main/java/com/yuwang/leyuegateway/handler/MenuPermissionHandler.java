package com.yuwang.leyuegateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单权限验证处理器
 *
 * @author Trae
 * @since 2024-01-20
 */
@Component
public class MenuPermissionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MenuPermissionHandler.class);
    
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 验证用户是否有访问指定路径的权限
     *
     * @param exchange ServerWebExchange
     * @param path 请求路径
     * @param menus 用户菜单列表
     * @return 如果有权限返回空Mono，否则返回403响应
     */
    public Mono<Void> checkPermission(ServerWebExchange exchange, String path, List<?> menus) {
        if (menus == null || menus.isEmpty()) {
            logger.warn("用户菜单为空，拒绝访问路径: {}", path);
            return forbidden(exchange, "用户无任何菜单权限");
        }
        
        // 检查用户菜单中是否包含当前路径的访问权限
        boolean hasPermission = menus.stream().anyMatch(menu -> {
            if (menu instanceof Map) {
                Map<?, ?> menuMap = (Map<?, ?>) menu;
                String menuPath = (String) menuMap.get("path");
                String menuUrl = (String) menuMap.get("url");
                
                // 检查菜单路径或URL是否匹配当前请求路径
                if (menuPath != null && matchesPath(menuPath, path)) {
                    return true;
                }
                if (menuUrl != null && matchesPath(menuUrl, path)) {
                    return true;
                }
                
                // 检查是否有通配符权限
                String permission = (String) menuMap.get("permission");
                if (permission != null && matchesPath(permission, path)) {
                    return true;
                }
            }
            return false;
        });
        
        if (!hasPermission) {
            logger.warn("用户无权限访问路径: {}", path);
            return forbidden(exchange, "用户无权限访问该资源");
        }
        
        return Mono.empty(); // 有权限，返回空Mono
    }
    
    /**
     * 检查路径是否匹配
     */
    private boolean matchesPath(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        
        // 使用Ant路径匹配器进行匹配
        return pathMatcher.match(pattern, path) || 
               pathMatcher.match(pattern + "/**", path) ||
               path.startsWith(pattern);
    }
    
    /**
     * 返回403禁止访问响应
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 403);
        result.put("message", message);
        result.put("success", false);
        
        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            DataBuffer buffer = response.bufferFactory().wrap(jsonResult.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            logger.error("序列化响应失败", e);
            return response.setComplete();
        }
    }
} 