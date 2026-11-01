package com.otboo.team03.user.repository;

import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.entity.User;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<User> findUsersByCursor(UserListRequest request) {
        throw new UnsupportedOperationException("아직 구현 안함");
    }
}