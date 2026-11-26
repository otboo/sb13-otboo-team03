-- 비공개 S3의 파일 식별자를 URL과 구분해 보관한다.
-- 기존 데이터 및 이미지 없는 의상을 위해 nullable로 추가한다.
-- image_url은 유지하며, 만료되는 서명 URL을 기존 URL에서 역추출하지 않는다.
ALTER TABLE clothes
    ADD COLUMN image_key VARCHAR(1024) NULL;
