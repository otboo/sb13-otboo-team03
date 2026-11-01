package com.otboo.team03.user.repository;

import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.entity.User;

import java.util.List;

public interface UserRepositoryCustom {
    List<User> findUsersByCursor(UserListRequest request);
    long countUsers(UserListRequest request);
}