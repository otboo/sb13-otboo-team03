package com.otboo.team03.user.init;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, AdminProperties adminProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    public void initialize() {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

        User admin = new User(
                adminProperties.email(),
                passwordEncoder.encode(adminProperties.password()),
                adminProperties.name()
        );
        admin.changeRole(UserRole.ADMIN);

        userRepository.save(admin);
    }


}