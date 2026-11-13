package com.otboo.team03.global.config;

import com.otboo.team03.auth.service.SocialLoginHandler;
import com.otboo.team03.global.security.PasswordAuthenticationProvider;
import com.otboo.team03.global.security.jwt.JwtAuthenticationFilter;
import com.otboo.team03.global.security.jwt.JwtLoginFailureHandler;
import com.otboo.team03.global.security.jwt.JwtLoginSuccessHandler;
import com.otboo.team03.global.security.jwt.JwtLogoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   PasswordAuthenticationProvider passwordAuthenticationProvider,
                                                   JwtLoginSuccessHandler jwtLoginSuccessHandler,
                                                   JwtLoginFailureHandler jwtLoginFailureHandler,
                                                   JwtLogoutHandler jwtLogoutHandler,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   AuthenticationEntryPoint authenticationEntryPoint,
                                                   AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .authenticationProvider(passwordAuthenticationProvider)
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health/readiness").permitAll()
                        .requestMatchers(
                                "/api/auth/csrf-token",
                                "/api/auth/sign-in",
                                "/api/auth/refresh",
                                "/api/auth/reset-password"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        // 공유 정의 조회는 로그인 사용자에게 허용하고, 등록·수정·삭제는 관리자만 허용한다.
                        // 구체적인 규칙을 anyRequest보다 먼저 선언해야 한다.
                        .requestMatchers(HttpMethod.POST, "/api/clothes/attribute-defs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/clothes/attribute-defs/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/clothes/attribute-defs/*").hasRole("ADMIN")
                        // WebSocket 핸드셰이크는 허용하고, 실제 JWT 인증은 STOMP CONNECT에서 처리한다.
                        .requestMatchers("/ws/**").permitAll()
                        // API 인증은 SPA 공개 규칙보다 먼저 검사한다.
                        .requestMatchers("/api/**").authenticated()
                        // 페이지 자체는 공개하고, 데이터 접근 권한은 기존 API 규칙으로 검사한다.
                        .requestMatchers(SpaPageRequest::matches).permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/logo_symbol.svg",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .formLogin(login -> login
                        .loginProcessingUrl("/api/auth/sign-in")
                        .successHandler(jwtLoginSuccessHandler)
                        .failureHandler(jwtLoginFailureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/sign-out")
                        .addLogoutHandler(jwtLogoutHandler)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Google, Kakao 로그인 시작과 콜백만 처리하는 전용 필터 체인이다.
    // 소셜 인증 이후의 API 요청은 기존 JWT 인증으로 처리한다.
    @Bean
    @Profile({"dev", "prod"})
    @Order(1)
    public SecurityFilterChain socialLoginSecurityFilterChain(HttpSecurity http,
                                                              SocialLoginHandler socialLoginHandler) throws Exception {

        http
                // 소셜 로그인 시작과 콜백만 이 필터 체인에서 처리한다.
                .securityMatcher("/oauth2/authorization/*", "/login/oauth2/code/*")
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .oauth2Login(oauth2 -> oauth2
                        // OAuth 인증 결과는 세션에 저장하지 않고 기존 JWT 인증으로 이어간다.
                        .securityContextRepository(new NullSecurityContextRepository())
                        .successHandler(socialLoginHandler)
                        .failureHandler(socialLoginHandler)
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // JwtAuthenticationFilter가 서블릿 필터로 이중 등록되는 것을 방지
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);

        registration.setEnabled(false);

        return registration;
    }
}
