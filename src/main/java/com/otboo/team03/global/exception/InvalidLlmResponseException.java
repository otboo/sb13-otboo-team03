package com.otboo.team03.global.exception;

/** 외부 LLM 응답이 누락·거부되었거나 끝까지 완료되지 않은 경우를 나타낸다. */
public class InvalidLlmResponseException extends RuntimeException {
    public InvalidLlmResponseException() {
        super("LLM 응답이 유효하지 않습니다.");
    }
}
