package com.otboo.team03.global.config;

import com.otboo.team03.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpaRoutingTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void browserPagesReachFrontendRouter() throws Exception {
        for (String path : new String[]{"/closet", "/auth/login", "/admin/clothes-attributes", "/404", "/unknown/page"}) {
            mvc.perform(get(path).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void missingApisAndFilesKeep404EvenWithHtmlAccept() throws Exception {
        for (String path : new String[]{"/api/unknown", "/api", "/assets/missing.js", "/missing.png", "/error"}) {
            mvc.perform(get(path).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.exceptionName").value("NOT_FOUND_RESOURCE"));
        }
        mvc.perform(get("/unknown").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyHtmlPageNavigationIsPublic() {
        for (String path : new String[]{"/api/clothes", "/api", "/assets/x", "/v3/api-docs", "/swagger-ui/index.html"}) {
            assertThat(SpaPageRequest.matches(htmlRequest("GET", path))).isFalse();
        }
        assertThat(SpaPageRequest.matches(htmlRequest("POST", "/closet"))).isFalse();
        assertThat(SpaPageRequest.matches(htmlRequest("GET", "/closet"))).isTrue();
        assertThat(SpaPageRequest.matches(htmlRequest("HEAD", "/404"))).isTrue();
        MockHttpServletRequest request = htmlRequest("GET", "/unknown");
        request.removeHeader("Accept");
        assertThat(SpaPageRequest.matches(request)).isFalse();
        request.addHeader("Accept", "*/*");
        assertThat(SpaPageRequest.matches(request)).isFalse();
        request.removeHeader("Accept");
        request.addHeader("Accept", "text/html;q=0");
        assertThat(SpaPageRequest.matches(request)).isFalse();
    }

    private MockHttpServletRequest htmlRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Accept", "text/html");
        return request;
    }

    @Configuration
    @EnableWebMvc
    static class WebConfig implements WebMvcConfigurer {
        @Bean
        GlobalExceptionHandler exceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        }
    }
}
