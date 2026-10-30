package com.otboo.team03.user.init;

import com.otboo.team03.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

public class AdminInitializer {

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            String email, String password, String name) {
    }

    public void initialize() {
        // TODO: Green 단계에서 구현
    }
}
