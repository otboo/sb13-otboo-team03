package com.otboo.team03.recommendation.rag;

/** 본문·옷장·키를 포함하지 않는 검색 프로토콜 오류. */
public final class ReferenceRetrievalException extends RuntimeException {
    public ReferenceRetrievalException() { super("스타일 검색 모델 또는 임베딩 응답이 올바르지 않습니다."); }
}
