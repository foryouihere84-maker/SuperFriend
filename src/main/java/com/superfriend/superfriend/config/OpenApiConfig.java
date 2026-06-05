package com.superfriend.superfriend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Swagger/OpenAPI 配置类
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI harmonyNotesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HarmonyNotes API")
                        .description("HarmonyNotes 智能笔记管理系统 - RESTful API 文档")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("HarmonyNotes Team")
                                .email("support@harmonynotes.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(Arrays.asList(
                        new Server()
                                .url("http://localhost:8080")
                                .description("本地开发环境"),
                        new Server()
                                .url("http://api.harmonynotes.com")
                                .description("生产环境")
                ));
    }
}
