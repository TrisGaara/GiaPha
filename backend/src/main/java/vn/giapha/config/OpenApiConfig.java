package vn.giapha.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Tai lieu API: /api/swagger-ui.html (dang nhap qua /api/auth/login roi bam "Authorize" dan token) */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Gia Phả Việt API", version = "v1",
        description = "Hệ thống quản lý gia phả đa dòng họ"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
