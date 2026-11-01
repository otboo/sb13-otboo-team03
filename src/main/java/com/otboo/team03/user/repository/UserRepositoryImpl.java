package com.otboo.team03.user.repository;

import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.entity.User;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.otboo.team03.user.entity.QUser.user;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<User> findUsersByCursor(UserListRequest request) {
        boolean ascending = "ASCENDING".equals(request.sortDirection());

        OrderSpecifier<?> primaryOrder = "createdAt".equals(request.sortBy())
                ? (ascending ? user.createdAt.asc() : user.createdAt.desc())
                : (ascending ? user.email.asc() : user.email.desc());

        OrderSpecifier<?> idOrder = ascending ? user.id.asc() : user.id.desc();

        return queryFactory
                .selectFrom(user)
                .orderBy(primaryOrder, idOrder)
                .limit(request.limit() + 1L)
                .fetch();
    }
}