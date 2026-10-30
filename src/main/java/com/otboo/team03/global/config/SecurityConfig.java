package com.otboo.team03.global.config;

import com.otboo.team03.global.security.jwt.JwtAuthenticationFilter;
import com.otboo.team03.global.security.jwt.JwtLoginFailureHandler;
import com.otboo.team03.global.security.jwt.JwtLoginSuccessHandler;
import com.otboo.team03.global.security.jwt.JwtLogoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtLoginSuccessHandler jwtLoginSuccessHandler,
                                                   JwtLoginFailureHandler jwtLoginFailureHandler,
                                                   JwtLogoutHandler jwtLogoutHandler,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   AuthenticationEntryPoint authenticationEntryPoint,
                                                   AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/csrf-token",
                                "/api/auth/sign-in",
                                "/api/auth/refresh"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // 조회는 로그인 사용자에게 열고, 공유 정의 등록만 관리자로 제한한다.
                        // 구체적인 규칙을 anyRequest보다 먼저 선언해야 한다.
                        .requestMatchers(HttpMethod.POST, "/api/clothes/attribute-defs").hasRole("ADMIN")
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
