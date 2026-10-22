package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.TestIds;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateWorkOrderRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresEveryCommonReportFieldAndAtLeastOneAttachment() {
        CreateWorkOrderRequestDTO request = new CreateWorkOrderRequestDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(
                        "location",
                        "gpsCoordinates",
                        "reason",
                        "phone",
                        "contactPerson",
                        "reportType",
                        "priority",
                        "uploadedAttachmentIds"
                );
    }

    @Test
    void rejectsNullAttachmentIdentifier() {
        CreateWorkOrderRequestDTO request = validRequest();
        request.setUploadedAttachmentIds(java.util.Collections.singletonList(null));

        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("uploadedAttachmentIds[0].<list element>");
                    assertThat(violation.getMessage()).isEqualTo("现场附件标识不能为空");
                });
    }

    @Test
    void acceptsCompleteReportRequest() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void acceptsTenAttachmentsAndRejectsTheEleventh() {
        CreateWorkOrderRequestDTO request = validRequest();
        request.setUploadedAttachmentIds(java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(TestIds::uuid)
                .toList());
        assertThat(validator.validate(request)).isEmpty();

        request.setUploadedAttachmentIds(java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(TestIds::uuid)
                .toList());
        assertThat(validator.validate(request))
                .extracting("message")
                .contains("现场附件不能超过10份");
    }

    private CreateWorkOrderRequestDTO validRequest() {
        CreateWorkOrderRequestDTO request = new CreateWorkOrderRequestDTO();
        request.setLocation("漳州市芗城区网络机房");
        request.setGpsCoordinates("117.647481,24.512948");
        request.setReason("箱体门锁损坏");
        request.setPhone("13000000095");
        request.setContactPerson("张三");
        request.setReportType(WorkOrderType.NETWORK_FAULT);
        request.setPriority(WorkOrderPriority.ROUTINE);
        request.setUploadedAttachmentIds(List.of(TestIds.uuid(2048)));
        return request;
    }
}
