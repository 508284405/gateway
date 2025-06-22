package com.yuwang.leyuegateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwang.leyuegateway.config.GatewayAuthProperties;
import com.yuwang.leyuegateway.constant.AuthHeaders;
import com.yuwang.leyuegateway.handler.MenuPermissionHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JWT认证全局过滤器
 *
 * @author Trae
 * @since 2024-01-20
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
    
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Resource
    private GatewayAuthProperties authProperties;
    
    @Resource
    private MenuPermissionHandler menuPermissionHandler;
    
    @SuppressWarnings("null")
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // 检查是否在白名单中
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }
        
        // 获取Authorization头
        String authHeader = request.getHeaders().getFirst("Authorization");
        
        // 检查Authorization头
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少或无效的Authorization头");
        }
        
        // 提取Token
        String token = authHeader.substring(7);
        
        try {
            // 验证Token并解析用户信息
            Claims claims = validateTokenAndGetClaims(token);
            
            // 提取用户信息
            String userId = claims.getSubject();
            String username = (String) claims.get("username");
            List<?> roles = (List<?>) claims.get("roles");
            List<?> menus = (List<?>) claims.get("menus");
            
            // 构建用户角色字符串
            String roleString = "";
            if (roles != null && !roles.isEmpty()) {
                roleString = roles.stream()
                        .map(role -> {
                            if (role instanceof Map) {
                                return ((Map<?, ?>) role).get("name");
                            }
                            return role.toString();
                        })
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
            }
            
            // 构建用户菜单字符串
            String menuString = "";
            if (menus != null && !menus.isEmpty()) {
                try {
                    menuString = objectMapper.writeValueAsString(menus);
                } catch (JsonProcessingException e) {
                    logger.warn("序列化菜单信息失败", e);
                    menuString = "[]";
                }
            }
            
            // 菜单权限验证
            if (authProperties.isEnableMenuPermission() && !isMenuPermissionWhitelisted(path)) {
                return menuPermissionHandler.checkPermission(exchange, path, menus)
                        .then(buildAndContinueRequest(exchange, chain, userId, username, roleString, menuString))
                        .switchIfEmpty(buildAndContinueRequest(exchange, chain, userId, username, roleString, menuString));
            } else {
                // 不需要菜单权限验证，直接添加用户信息到请求头
                return buildAndContinueRequest(exchange, chain, userId, username, roleString, menuString);
            }
            
        } catch (Exception e) {
            logger.error("JWT验证失败: token={}, path={}, error={}", 
                    token.substring(0, Math.min(token.length(), 20)) + "...", 
                    path, e.getMessage());
            return unauthorized(exchange, "Token验证失败");
        }
    }
    
    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        List<String> whitelist = authProperties.getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
    
    /**
     * 检查路径是否在菜单权限验证白名单中
     */
    private boolean isMenuPermissionWhitelisted(String path) {
        List<String> whitelist = authProperties.getMenuPermissionWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
    
    /**
     * 验证Token并获取Claims
     */
    private Claims validateTokenAndGetClaims(String token) {
        PublicKey publicKey = getPublicKey();
        
        Jws<Claims> claimsJws = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .setAllowedClockSkewSeconds(authProperties.getClockSkew())
                .build()
                .parseClaimsJws(token);
        
        return claimsJws.getBody();
    }
    
    /**
     * 获取RSA公钥
     */
    private PublicKey getPublicKey() {
        try {
            String publicKeyPEM = authProperties.getPublicKey()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyPEM);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("无法加载RSA公钥", e);
        }
    }
    
    /**
     * 返回401未授权响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 401);
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
    
    private Mono<Void> buildAndContinueRequest(ServerWebExchange exchange, GatewayFilterChain chain, String userId, String username, String roleString, String menuString) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(AuthHeaders.USER_ID, userId)
                .header(AuthHeaders.USERNAME, username != null ? username : "")
                .header(AuthHeaders.USER_ROLES, roleString)
                .header(AuthHeaders.USER_MENUS, menuString)
                .build();
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
    
    @Override
    public int getOrder() {
        return -100; // 确保在其他过滤器之前执行
    }
} 