package com.otboo.team03.global.storage;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AWS 클라이언트를 대체하여 외부 접속·실제 버킷 변경 없이 저장 요청과 검증을 확인한다.
 * 파일 형식 테스트는 현재 구현의 헤더 식별 규칙을 검증하며 전체 이미지 디코딩 검증은 아니다.
 */
class S3ImageStorageTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwC"
                    + "AAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    private final S3Client client = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final S3ImageStorage storage = new S3ImageStorage(
            client, presigner, new S3Properties("test-bucket", "ap-northeast-2", 5));

    static Stream<Arguments> supportedFormats() {
        // JPEG/WebP는 실제 사진 대신 형식 식별용 헤더를 사용한다.
        return Stream.of(
                Arguments.of("jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}),
                Arguments.of("png", "image/png", PNG),
                Arguments.of("webp", "image/webp", "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)));
    }

    @ParameterizedTest
    @MethodSource("supportedFormats")
    @DisplayName("파일명과 MIME 대신 내용으로 저장 형식을 결정하고 원본 바이트를 업로드한다")
    void uploadsDetectedFormatAndOriginalContent(String extension, String contentType, byte[] content) throws Exception {
        var image = new MockMultipartFile("image", "../../original.exe", "application/octet-stream", content);
        // 실제 SDK처럼 스트림이 열려 있는 업로드 호출 시점에 본문을 읽는다.
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            RequestBody body = invocation.getArgument(1);
            assertThat(body.contentLength()).isEqualTo((long) content.length);
            try (var input = body.contentStreamProvider().newStream()) {
                assertThat(input.readAllBytes()).isEqualTo(content);
            }
            return PutObjectResponse.builder().build();
        });
        String key = storage.upload(image);
        var captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captor.capture(), any(RequestBody.class));
        var request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(key);
        assertThat(request.contentType()).isEqualTo(contentType);
        assertThat(request.acl()).isNull(); // 공개 ACL을 지정하지 않는다.
        assertThat(key).startsWith("clothes/").endsWith("." + extension);
        String uuid = key.substring("clothes/".length(), key.lastIndexOf('.'));
        assertThat(UUID.fromString(uuid).toString()).isEqualTo(uuid);
        verifyNoInteractions(presigner);
    }

    @Test
    void repeatedUploadsUseDifferentKeys() {
        var image = new MockMultipartFile("image", PNG);
        assertThat(storage.upload(image)).isNotEqualTo(storage.upload(image));
    }

    @Test
    @DisplayName("미첨부·빈 파일·용량 초과는 업로드하지 않고 거부한다")
    void rejectsMissingEmptyAndOversizedFiles() throws Exception {
        assertError(() -> storage.upload(null), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> storage.upload(new MockMultipartFile("image", new byte[0])), ErrorCode.INVALID_INPUT_VALUE);
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        assertError(() -> storage.upload(oversized), ErrorCode.INVALID_INPUT_VALUE);
        verify(oversized, never()).getInputStream();
        verifyNoInteractions(client, presigner);
    }

    @Test
    void acceptsExactSizeLimit() {
        var content = Arrays.copyOf(PNG, 5 * 1024 * 1024);
        assertThat(storage.upload(new MockMultipartFile("image", content))).endsWith(".png");
        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "GIF89a", "RIFF0000WAVE", "RIFF", "123456789012", "RIFF0000WEBX"})
    void rejectsUnsupportedAndIncompleteHeaders(String value) {
        assertError(() -> storage.upload(new MockMultipartFile("image", "image.png", "image/png",
                value.getBytes(StandardCharsets.US_ASCII))), ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(client, presigner);
    }

    @Test
    void readingFailureDuringDetectionIsReported() throws Exception {
        MultipartFile image = mock(MultipartFile.class);
        when(image.getSize()).thenReturn(100L);
        when(image.getInputStream()).thenThrow(new IOException("read failed"));
        assertError(() -> storage.upload(image), ErrorCode.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(client);
    }

    @Test
    void readingFailureDuringUploadIsReported() throws Exception {
        MultipartFile image = mock(MultipartFile.class);
        when(image.getSize()).thenReturn((long) PNG.length);
        when(image.getInputStream()).thenReturn(new ByteArrayInputStream(PNG))
                .thenThrow(new IOException("second stream failed"));
        assertError(() -> storage.upload(image), ErrorCode.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(client);
    }

    @Test
    void sdkUploadFailurePropagatesAndClosesStream() throws Exception {
        MultipartFile image = mock(MultipartFile.class);
        var uploadStream = spy(new ByteArrayInputStream(PNG));
        when(image.getSize()).thenReturn((long) PNG.length);
        when(image.getInputStream()).thenReturn(new ByteArrayInputStream(PNG), uploadStream);
        var failure = SdkClientException.create("connection failed");
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(failure);
        assertThatThrownBy(() -> storage.upload(image)).isSameAs(failure);
        verify(uploadStream).close();
    }

    @Test
    void createsUrlForConfiguredBucketKeyAndDuration() throws Exception {
        var signed = mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://example.com/image?signature=test").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
        assertThat(storage.getAccessUrl("clothes/test.png")).isEqualTo("https://example.com/image?signature=test");
        var captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("clothes/test.png");
        verifyNoInteractions(client); // URL 생성 시 파일 다운로드나 존재 확인을 요청하지 않는다.
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingKeyDoesNotCallAws(String key) {
        assertThat(storage.getAccessUrl(key)).isNull();
        storage.delete(key);
        verifyNoInteractions(client, presigner);
    }

    @Test
    void deletesOnlyRequestedObject() {
        storage.delete("clothes/old.png");
        verify(client).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key("clothes/old.png").build());
        verifyNoMoreInteractions(client);
        verifyNoInteractions(presigner);
    }

    @Test
    void sdkDeleteFailureIsNotSwallowed() {
        var failure = SdkClientException.create("delete failed");
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(failure);
        assertThatThrownBy(() -> storage.delete("clothes/old.png")).isSameAs(failure);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action).isInstanceOfSatisfying(OtbooException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }
}
