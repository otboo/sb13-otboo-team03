package com.otboo.team03.user.repository;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);
    boolean existsByRole(UserRole role);
    Optional<User> findByEmail(String email);
    long countByRole(UserRole role);

}
