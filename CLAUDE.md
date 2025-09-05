# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Leyue Gateway is a Spring Cloud Gateway-based microservice that provides JWT authentication and authorization for the Leyue ecosystem. It acts as an API gateway with integrated security features including JWT token validation, role-based access control, and menu-based permission management.

## Architecture

### Core Components

- **JWT Authentication Filter** (`JwtAuthGlobalFilter`): Global filter that validates JWT tokens using RSA public key verification, extracts user information, and adds user context to downstream services via headers
- **Menu Permission Handler** (`MenuPermissionHandler`): Validates user permissions against menu-based access control using Ant path matching
- **Gateway Auth Properties** (`GatewayAuthProperties`): Configuration properties for JWT validation, whitelists, and permission settings
- **Auth Headers Constants** (`AuthHeaders`): Defines standard headers for passing user context to downstream services

### Security Model

- RSA-based JWT token validation with configurable clock skew tolerance
- Two-tier security: JWT authentication + optional menu-based authorization
- Whitelist-based bypass for public endpoints
- Automatic user context propagation via HTTP headers (`X-User-Id`, `X-Username`, `X-User-Roles`, `X-User-Menus`)

### Service Discovery

- Integrates with Alibaba Nacos for service discovery and configuration management
- Dynamic route creation from service registry
- Configuration is externalized to Nacos config server

## Development Commands

### Build and Package
```bash
mvn clean package -DskipTests          # Build without tests
mvn clean package -DskipTests -Pprod   # Build with production profile
```

### Run Application
```bash
mvn spring-boot:run                    # Run locally
java -jar target/leyue-gateway-*.jar   # Run packaged JAR
```

### Docker Operations
```bash
docker build -t leyue-gateway .        # Build Docker image
docker run -p 8080:8080 leyue-gateway  # Run container
```

### Dependency Management
```bash
mvn dependency:tree                    # View dependency tree
mvn dependency:analyze                 # Analyze dependencies
```

## Configuration Structure

- **bootstrap.yml**: Nacos connection and service discovery configuration
- **application.yml**: Local application settings (port, gateway configuration, logging)
- **JWT Configuration**: Managed through Nacos config server with the following properties:
  - `jwt.publicKey`: RSA public key for token verification
  - `jwt.whitelist`: Authentication bypass patterns
  - `jwt.enableMenuPermission`: Toggle for menu-based authorization
  - `jwt.menuPermissionWhitelist`: Authorization bypass patterns

## Key Implementation Details

### JWT Token Flow
1. Extract Bearer token from Authorization header
2. Validate token signature using RSA public key
3. Extract user claims (userId, username, roles, menus)
4. Check menu permissions if enabled
5. Add user context headers for downstream services

### Filter Execution Order
- `JwtAuthGlobalFilter` executes with order -100 to ensure it runs before other filters
- Menu permission validation occurs after JWT validation but before request forwarding

## Dependencies

- Spring Boot 3.4.3 with Java 17
- Spring Cloud Gateway for routing
- Spring Cloud Alibaba for Nacos integration
- JJWT 0.11.5 for JWT processing
- Apache Commons Lang3 for utilities

## Port Configuration

- Default application port: 8080
- Health check endpoint: `/actuator/health`
- Gateway discovery locator enabled for dynamic routing