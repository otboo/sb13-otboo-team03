
package com.otboo.team03.global.security.jwt;

import com.otboo.team03.global.security.OtbooUserDetailsService;
import com.otboo.team03.global.exception.jwt.InvalidJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRegistry jwtRegistry;
    private final OtbooUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {

            String token = authorizationHeader.substring(7);

            try {
                jwtTokenProvider.validateAccessToken(token);

                if (jwtRegistry.hasActiveJwtInformationByAccessToken(token)) {

                    UUID userId = jwtTokenProvider.getUserId(token);
                    UserDetails userDetails = userDetailsService.loadUserById(userId);

                    if (userDetails.isEnabled()
                            && userDetails.isAccountNonLocked()
                            && userDetails.isAccountNonExpired()
                            && userDetails.isCredentialsNonExpired()) {

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        SecurityContextHolder.getContext()
                                .setAuthentication(authentication);
                    }
                }
            } catch (InvalidJwtException | UsernameNotFoundException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
