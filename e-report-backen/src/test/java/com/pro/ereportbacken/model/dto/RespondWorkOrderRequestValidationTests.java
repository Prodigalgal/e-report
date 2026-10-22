package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.TestIds;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RespondWorkOrderRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsTenAttachmentsAndRejectsTheEleventh() {
        RespondWorkOrderRequestDTO request = validRequest();
        request.setUploadedAttachmentIds(IntStream.rangeClosed(1, 10)
                .mapToObj(TestIds::uuid)
                .toList());
        assertThat(validator.validate(request)).isEmpty();

        request.setUploadedAttachmentIds(IntStream.rangeClosed(1, 11)
                .mapToObj(TestIds::uuid)
                .toList());
        assertThat(validator.validate(request))
                .extracting("message")
                .contains("回单附件不能超过10份");
    }

    @Test
    void rejectsNullAttachmentIdentifier() {
        RespondWorkOrderRequestDTO request = validRequest();
        request.setUploadedAttachmentIds(java.util.Collections.singletonList(null));

        assertThat(validator.validate(request))
                .extracting("message")
                .contains("回单附件标识不能为空");
    }

    private RespondWorkOrderRequestDTO validRequest() {
        RespondWorkOrderRequestDTO request = new RespondWorkOrderRequestDTO();
        request.setExpectedVersion(1L);
        request.setResolutionDetails("现场故障已经处理完成");
        request.setResolutionMethod("重新熔接并测试业务");
        return request;
    }
}
