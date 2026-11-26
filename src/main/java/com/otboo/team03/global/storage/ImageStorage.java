package com.otboo.team03.global.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 파일 저장과 접근을 담당하는 공통 기능
 * 도메인 서비스는 S3 SDK를 직접 사용하지 않고, 이 인터페이스를 호출한다.
 */
public interface ImageStorage {

    /** object key와 URL 구분 중요
     * object key
     * clothes/파일ID.jpg
     * → S3에서 파일을 찾거나 삭제할 때 사용
     *
     * 접근 URL
     * https://...
     * → 프런트에서 이미지를 표시할 때 사용
     */


    /**
     * 이미지를 저장하고 저장소 내부 식별자인 object key를 반환한다.
     * 예를 들면, clothes/550e8400.jpg
     * 반환값은 브라우저에서 바로 사용할 URL이 아님!
     */
    String upload(MultipartFile image);

    /**
     * 저장된 이미지에 접근할 URL을 생성한다.
     * 비공개 s3를 사용하면 유효기간이 있는 서명 URL을 반환할 수 있다.
     */

    String getAccessUrl(String objectKey);

    /**
     * object key에 해당하는 파일을 삭제한다.
     * 이미지 교체, 의상 삭제, 저장 실패 시 정리에 사용한다.
     */
    void delete(String objectKey);
}
