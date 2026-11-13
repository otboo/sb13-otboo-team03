package com.otboo.team03.auth.repository;

import com.otboo.team03.auth.entity.SocialAccount;
import com.otboo.team03.auth.entity.SocialProvider;
import com.otboo.team03.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    boolean existsByUser(User user);
}
