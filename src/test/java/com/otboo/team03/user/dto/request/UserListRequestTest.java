package com.otboo.team03.user.dto.request;

import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserListRequestTest {

    @Nested
    @DisplayName("기본 목록 첫 페이지 판별")
    class IsDefaultFirstPage {

        @Test
        @DisplayName("필터와 커서가 없으면 기본 첫 페이지로 판단한다")
        void isDefaultFirstPage_returnsTrueWithoutFilters() {
            UserListRequest request = createRequest(null, null, null, null, null);

            assertThat(request.isDefaultFirstPage()).isTrue();
        }

        @Test
        @DisplayName("이메일 검색어가 빈 문자열 또는 공백이면 기본 첫 페이지로 판단한다")
        void isDefaultFirstPage_returnsTrueForBlankEmail() {
            UserListRequest emptyEmail = createRequest(null, null, "", null, null);
            UserListRequest blankEmail = createRequest(null, null, "   ", null, null);

            assertThat(emptyEmail.isDefaultFirstPage()).isTrue();
            assertThat(blankEmail.isDefaultFirstPage()).isTrue();
        }

        @Test
        @DisplayName("커서 또는 필터가 존재하면 기본 첫 페이지가 아니라고 판단한다")
        void isDefaultFirstPage_returnsFalseWithCursorOrFilters() {
            UserListRequest cursor = createRequest("cursor", null, null, null, null);
            UserListRequest idAfter = createRequest(null, UUID.randomUUID(), null, null, null);
            UserListRequest email = createRequest(null, null, "test", null, null);
            UserListRequest role = createRequest(null, null, null, UserRole.ADMIN, null);
            UserListRequest locked = createRequest(null, null, null, null, false);

            assertThat(cursor.isDefaultFirstPage()).isFalse();
            assertThat(idAfter.isDefaultFirstPage()).isFalse();
            assertThat(email.isDefaultFirstPage()).isFalse();
            assertThat(role.isDefaultFirstPage()).isFalse();
            assertThat(locked.isDefaultFirstPage()).isFalse();
        }
    }

    private UserListRequest createRequest(
            String cursor, UUID idAfter, String emailLike, UserRole roleEqual, Boolean locked
    ) {
        return new UserListRequest(
                cursor, idAfter, 10, "email", "ASCENDING", emailLike, roleEqual, locked
        );
    }
}
