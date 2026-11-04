package com.otboo.team03.global.exception;

import lombok.extern.slf4j.Slf4j;
import com.otboo.team03.global.config.SpaPageRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OtbooException.class)
    public ResponseEntity<ErrorResponse> handleOtbooException(OtbooException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ErrorResponse errorResponse = ErrorResponse.from(errorCode, ex.getDetails());

        log.warn("Business exception: code={}, message={}", errorCode, ex.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        Map<String, String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> Objects.toString(error.getDefaultMessage(), ""),
                        (first, second) -> first
                ));

        ErrorResponse errorResponse = ErrorResponse.from(errorCode, details);

        log.warn("Validation exception: code={}, details={}", errorCode, details);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;
        ErrorResponse errorResponse = ErrorResponse.from(errorCode);

        log.warn("Access denied: message={}", ex.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;
        ErrorResponse errorResponse = ErrorResponse.from(errorCode);

        log.warn("Authentication exception: message={}", ex.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse);
    }

    /** Controller 호출 전 발생하는 타입 변환 실패도 클라이언트 입력 오류로 응답한다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorCode code = ErrorCode.INVALID_INPUT_VALUE;
        return ResponseEntity.status(code.getHttpStatus()).body(ErrorResponse.from(
                code, Map.of(ex.getName(), "요청 값의 형식 또는 허용 범위가 올바르지 않습니다.")));
    }

    /** 필수 쿼리 파라미터 누락을 내부 서버 오류와 구분한다. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        ErrorCode code = ErrorCode.INVALID_INPUT_VALUE;
        return ResponseEntity.status(code.getHttpStatus()).body(ErrorResponse.from(
                code, Map.of(ex.getParameterName(), "필수 요청 파라미터입니다.")));
    }

    /** Multipart의 필수 JSON 파트 누락과 용량 초과도 클라이언트 입력 오류다. */
    @ExceptionHandler({MissingServletRequestPartException.class, MaxUploadSizeExceededException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleMultipartInput(Exception ex) {
        ErrorCode code = ErrorCode.INVALID_INPUT_VALUE;
        return ResponseEntity.status(code.getHttpStatus()).body(ErrorResponse.from(code));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(415).body(ErrorResponse.from(ErrorCode.INVALID_INPUT_VALUE));
    }

    /** 알려진 화면과 없는 화면 모두 프런트 라우터가 처리하며, 없는 API·파일은 404로 응답한다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        if (SpaPageRequest.matches(request)) {
            return new ModelAndView("forward:/index.html");
        }
        return ResponseEntity.status(404).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(ErrorCode.NOT_FOUND_RESOURCE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponse errorResponse = ErrorResponse.from(errorCode);

        log.error("Server exception: code={}", errorCode, ex);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse);
    }

}
