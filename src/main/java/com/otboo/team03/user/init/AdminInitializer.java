package com.otboo.team03.user.init;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

public class AdminInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            String email, String password, String name) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    public void initialize() {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User admin = new User(email, passwordEncoder.encode(password), name);
        admin.changeRole(UserRole.ADMIN);

        userRepository.save(admin);
    }
}