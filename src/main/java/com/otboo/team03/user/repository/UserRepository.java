package com.otboo.team03.user.repository;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {

    boolean existsByEmail(String email);

    boolean existsByRole(UserRole role);

    Optional<User> findByEmail(String email);

    long countByRole(UserRole role);

    long countByRoleAndLockedFalse(UserRole role);

    // 관리자 권한 변경 및 계정 잠금 시 공통 PostgreSQL 트랜잭션 락 획득
    // 여러 인스턴스의 동시 요청을 직렬화하여 마지막 활성 ADMIN을 보호
    // 720072는 전용 락 식별자이며, 트랜잭션 종료 시 자동 해제
    @Query(value = """
        SELECT 1
        FROM (SELECT pg_advisory_xact_lock(:lockId)) AS admin_lock
        """, nativeQuery = true)
    int acquireAdminLock(@Param("lockId") long lockId);
}
