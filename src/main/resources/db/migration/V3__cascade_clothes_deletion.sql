-- 의상 삭제 정책: 소유자 권한은 Service에서 확인하고, 참조 행 정리는 DB에서 수행한다.
-- clothes가 삭제되는 방향에만 CASCADE를 적용한다. 피드 연결 삭제가 의상 삭제로 전파되지는 않는다.
-- definition_id 외래 키는 변경하지 않아 사용 중인 공유 정의 삭제는 계속 차단한다.
-- 이미 적용된 V1/V2는 수정하지 않고 프로젝트 공통 다음 버전(V3)으로 추가한다.
-- 의상 삭제 시 의상별 속성값을 정리한다. 공유 속성 정의는 유지한다.
ALTER TABLE clothes_attribute
    DROP CONSTRAINT fk_clothes_to_clothes_attribute;
ALTER TABLE clothes_attribute
    ADD CONSTRAINT fk_clothes_to_clothes_attribute
        FOREIGN KEY (clothes_id) REFERENCES clothes (id) ON DELETE CASCADE;

-- 피드 본문은 유지하고 삭제된 의상과의 연결만 제거한다.
ALTER TABLE feed_clothes
    DROP CONSTRAINT fk_clothes_to_feed_clothes;
ALTER TABLE feed_clothes
    ADD CONSTRAINT fk_clothes_to_feed_clothes
        FOREIGN KEY (clothes_id) REFERENCES clothes (id) ON DELETE CASCADE;
