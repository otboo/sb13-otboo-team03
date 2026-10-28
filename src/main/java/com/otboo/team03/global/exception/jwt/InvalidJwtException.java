package com.otboo.team03.global.exception.jwt;

public class InvalidJwtException extends RuntimeException {
    public InvalidJwtException() {
        super("유효하지 않은 JWT입니다.");
    }
}
