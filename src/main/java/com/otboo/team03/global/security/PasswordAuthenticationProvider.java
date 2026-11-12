
package com.otboo.team03.global.security;

import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PasswordAuthenticationProvider extends DaoAuthenticationProvider {

    private final RedisTemporaryPasswordRepository temporaryPasswordRepository;

    public PasswordAuthenticationProvider(
            OtbooUserDetailsService userDetailsService,
            RedisTemporaryPasswordRepository temporaryPasswordRepository,
            PasswordEncoder passwordEncoder
    ) {
        super(userDetailsService);
        this.temporaryPasswordRepository = temporaryPasswordRepository;
        setPasswordEncoder(passwordEncoder);

        // 계정 상태 검사 실패 시 임시 비밀번호 조회 및 소모 방지
        setAlwaysPerformAdditionalChecksOnUser(false);
    }

    @Override
    protected void additionalAuthenticationChecks(
            UserDetails userDetails,
            UsernamePasswordAuthenticationToken authentication
    ) throws AuthenticationException {
        // 기존 비밀번호로 인증에 성공하면 임시 비밀번호는 사용하지 않는다.
        try {
            super.additionalAuthenticationChecks(userDetails, authentication);
            return;
        } catch (BadCredentialsException e) {
            // 기존 비밀번호가 일치하지 않으면 임시 비밀번호를 확인한다.
        }

        if (authentication.getCredentials() == null) {
            throw new BadCredentialsException("Bad credentials");
        }

        String rawPassword = authentication.getCredentials().toString();
        String email = userDetails.getUsername();

        try {
            Optional<String> temporaryPassword = temporaryPasswordRepository.findByEmail(email);

            if (temporaryPassword.isEmpty()
                    || !getPasswordEncoder().matches(rawPassword, temporaryPassword.get())
                    || !temporaryPasswordRepository.consumeIfMatches(email, temporaryPassword.get())) {
                throw new BadCredentialsException("Bad credentials");
            }
        } catch (DataAccessException e) {
            throw new AuthenticationServiceException("임시 비밀번호 인증 중 Redis 접근 실패", e);
        }
    }
}
