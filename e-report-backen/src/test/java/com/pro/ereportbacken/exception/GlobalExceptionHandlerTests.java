package com.pro.ereportbacken.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTests {

    @Test
    void malformedJsonReturnsAParameterErrorWithoutEchoingRequestContent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/admin/users/id/email-notifications");
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);

        var response = new GlobalExceptionHandler().handleUnreadableRequest(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("提交内容不完整或格式不正确。");
    }

    @Test
    void mapsInvalidEnumRequestParameterToBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/app-updates/latest");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleMethodArgumentTypeMismatchException(
                mock(MethodArgumentTypeMismatchException.class),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("请求参数取值不正确，请检查后重试。");
    }

    @Test
    void preservesProductNamesInFriendlyChineseMessages() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/admin/app-updates/r2/objects");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("不能删除当前稳定版的 R2 安装包，请先发布更高版本。"),
                request
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("R2").doesNotContain("r2");
    }

    @Test
    void explainsTheOneHundredMegabyteUploadLimit() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/file-transfers/uploads/id/chunks/0");

        var response = new GlobalExceptionHandler().handleMultipartException(
                new MaxUploadSizeExceededException(100L * 1024L * 1024L),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .contains("100兆字节")
                .doesNotContain("50兆字节");
    }
}
