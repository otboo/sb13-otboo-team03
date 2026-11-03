package com.otboo.team03.global.security.jwt;

import com.otboo.team03.user.dto.response.UserDto;
import lombok.Getter;

@Getter
public class JwtInformation {

    private final UserDto userDto;
    private String accessToken;
    private String refreshToken;

    public JwtInformation(UserDto userDto, String accessToken, String refreshToken) {
        this.userDto = userDto;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public void rotate(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

}
