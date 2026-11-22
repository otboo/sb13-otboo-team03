-- 동일 좌표 및 동일 예보 대상 시각에 대한 중복 적재 방지 복합 UNIQUE 제약조건 추가
ALTER TABLE weathers
    ADD CONSTRAINT uk_weathers_location_forecast_at UNIQUE (latitude, longitude, forecast_at);
