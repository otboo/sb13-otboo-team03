package com.otboo.team03.user.repository;

import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
                .where(
                        emailLike(request.emailLike()),
                        roleEqual(request.roleEqual()),
                        lockedEqual(request.locked()),
                        emailCursor(request),
                        createdAtCursor(request)
                )
                .orderBy(primaryOrder, idOrder)
                .limit(request.limit() + 1L)
                .fetch();
    }

    private BooleanExpression emailLike(String emailLike) {
        return emailLike == null || emailLike.isBlank() ? null : user.email.containsIgnoreCase(emailLike);
    }

    private BooleanExpression roleEqual(UserRole role) {
        return role == null ? null : user.role.eq(role);
    }

    private BooleanExpression lockedEqual(Boolean locked) {
        return locked == null ? null : user.locked.eq(locked);
    }

    private BooleanExpression emailCursor(UserListRequest request) {
        if (!"email".equals(request.sortBy()) || request.cursor() == null) {
            return null;
        }

        boolean ascending = "ASCENDING".equals(request.sortDirection());
        String cursor = request.cursor();
        UUID idAfter = request.idAfter();

        BooleanExpression primaryCondition = ascending
                ? user.email.gt(cursor)
                : user.email.lt(cursor);

        if (idAfter == null) {
            return primaryCondition;
        }

        BooleanExpression secondaryCondition = ascending
                ? user.id.gt(idAfter)
                : user.id.lt(idAfter);

        return primaryCondition.or(
                user.email.eq(cursor).and(secondaryCondition)
        );
    }

    private BooleanExpression createdAtCursor(UserListRequest request) {
        if (!"createdAt".equals(request.sortBy()) || request.cursor() == null) {
            return null;
        }

        boolean ascending = "ASCENDING".equals(request.sortDirection());
        Instant cursor = Instant.parse(request.cursor());
        UUID idAfter = request.idAfter();

        BooleanExpression primaryCondition = ascending
                ? user.createdAt.gt(cursor)
                : user.createdAt.lt(cursor);

        if (idAfter == null) {
            return primaryCondition;
        }

        BooleanExpression secondaryCondition = ascending
                ? user.id.gt(idAfter)
                : user.id.lt(idAfter);

        return primaryCondition.or(
                user.createdAt.eq(cursor).and(secondaryCondition)
        );
    }
}