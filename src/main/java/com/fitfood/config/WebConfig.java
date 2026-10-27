
package com.fitfood.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Path uploadDir = Path.of("uploads").toAbsolutePath().normalize();

            Files.createDirectories(uploadDir);

            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations(uploadDir.toUri().toString());

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Không thể tạo thư mục lưu ảnh sản phẩm.", e);
        }
    }
}
