package com.ecommerce.platform.modules.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    private final String resourceLocation;

    public StaticResourceConfig(@Value("${app.media.resource-location}") String resourceLocation) {
        this.resourceLocation = resourceLocation;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/images/**")
                .addResourceLocations(resourceLocation);
    }
}
