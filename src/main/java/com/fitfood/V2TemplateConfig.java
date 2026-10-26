package com.fitfood;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Giao dien moi nam trong templates/v2/. Voi moi view (vd "index"), Spring thu tim
 * templates/v2/index.html truoc; neu khong co thi dung templates/index.html nhu cu.
 * Controller, service, database KHONG bi anh huong. Tat: fitfood.theme.v2=false trong application.properties.
 */
@Configuration
@ConditionalOnProperty(name = "fitfood.theme.v2", havingValue = "true", matchIfMissing = true)
public class V2TemplateConfig {

    @Bean
    public SpringResourceTemplateResolver v2TemplateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setPrefix("classpath:/templates/v2/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        resolver.setCacheable(true);
        resolver.setOrder(1);
        return resolver;
    }
}
