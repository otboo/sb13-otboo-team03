package com.otboo.team03.global.storage;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * 비공개 s3에 이미지를 저장하고 접근 URL을 생성한ㄴ다.
 * 의상 소유자 확인과 DB 저장은 도메인 Service에서 담당
 */

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "otboo",
        name = "storage",
        havingValue = "s3"
)
public class S3ImageStorage implements ImageStorage{
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;
    //임의 설정5MiB
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    //저장된 object key로 이미지 조회용 서명 URL 생성
    //만료되는 URL이므로 DBDp 저장하지 않고 응답에 사용
    @Override
    public String getAccessUrl(String objectKey) {
        //이미지가 없는 의상은 URL도 반환하지 않는다.
        if(objectKey == null || objectKey.isBlank()){
            return null;
        }
        //조회할 때 파일의 위치를 지정
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        //조회 요청과 URL 유효기간을 묶는다.
        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .getObjectRequest(objectRequest)
                        .signatureDuration(
                                Duration.ofMinutes(properties.presignMinutes())
                        )
                        .build();
        //서명한 URL을 문자열로 반환한다.
        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toExternalForm();//URL객체를 문자열로 변환
        //지정한 파일의 임시 접근 URL만 만든다.
    }
    /**
     * 이미지를 검증한 뒤 비공개 S3에 저장하고 object key를 반환한다.
     * DB 저장과 실패 시 파일 정리는 호출하는 Service에서 담당한다.
     * 파일 크기 검사
     * → 헤더로 형식 식별
     * → UUID 기반 key 생성
     * → S3 업로드
     * → 성공한 경우 key 반환
     */
    @Override
    public String upload(MultipartFile image) {
        validateFileSize(image);

        String extension = detectImageExtension(image);

        // 클라이언트가 보낸 Content-Type 대신 식별한 형식을 사용한다.
        String contentType = switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        };

        // 원본 파일명을 사용하지 않아 이름 충돌과 경로 입력 문제를 방지한다.
        String objectKey = "clothes/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        try (InputStream input = image.getInputStream()) {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(input, image.getSize())
            );
        } catch (IOException e) {
            throw new OtbooException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return objectKey;
    }


    /**
     * object key에 해당하는 S3 파일을 삭제한다.
     * 의상·DB 데이터는 변경하지 않는다.
     */
    @Override
    public void delete(String objectKey) {
        // 이미지가 없는 경우에는 삭제할 파일도 없다.
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();

        s3Client.deleteObject(request);
    }
    /**
     * 실제 업로드할 파일의 존재 여부와 용량을 검사한다.
     * 이미지 미첨부 여부는 호출하는 Service에서 구분한다.
     */
    private void validateFileSize(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
    /** 파일 내용에서 이미지 형식을 식별, JPEG, PNG, WebP 기준 (변경 가능)
     * 파일 앞부분의 고유 표시로 형식을 식별한다.
     * 반환값은 서버에서 사용할 확장자다.
     * 전체 이미지의 손상 여부까지 검증하는 메서드는 아니다.
     * photo.png여도 내용이 jpeg라면 jpg를 반환, 저장 확장자는 식별한 형식을 기준으로 만드는 것
     * 이 검사는 파일 헤더 기준의 형식 식별, 헤더만 흉내 낸 파일이나 손상된 이미지까지 완전히 검증하는 것이 아닏!
     * 전체 이미지 디코딩 검증과 구분해야한다.
     */
    private String detectImageExtension(MultipartFile image) {
        try (InputStream input = image.getInputStream()) {
            byte[] header = input.readNBytes(12);

            // JPEG: FF D8 FF
            if (header.length >= 3
                    && header[0] == (byte) 0xFF
                    && header[1] == (byte) 0xD8
                    && header[2] == (byte) 0xFF) {
                return "jpg";
            }

            // PNG의 고정된 8바이트 표시
            byte[] pngSignature = {
                    (byte) 0x89, 0x50, 0x4E, 0x47,
                    0x0D, 0x0A, 0x1A, 0x0A
            };

            if (header.length >= 8
                    && Arrays.equals(
                    Arrays.copyOfRange(header, 0, 8),
                    pngSignature
            )) {
                return "png";
            }

            // WebP: RIFF로 시작하고 8~11번째 바이트가 WEBP
            if (header.length == 12
                    && "RIFF".equals(new String(
                    header, 0, 4, StandardCharsets.US_ASCII))
                    && "WEBP".equals(new String(
                    header, 8, 4, StandardCharsets.US_ASCII))) {
                return "webp";
            }

            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        } catch (IOException e) {
            throw new OtbooException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
