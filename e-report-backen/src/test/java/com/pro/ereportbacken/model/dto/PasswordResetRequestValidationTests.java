package com.pro.ereportbacken.model.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsMatchingPasswordConfirmation() {
        PasswordResetRequestDTO request = request("Correct-Horse-Battery-2026", "Correct-Horse-Battery-2026");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingOrDifferentPasswordConfirmation() {
        PasswordResetRequestDTO different = request("Correct-Horse-Battery-2026", "Different-Horse-Battery-2026");
        PasswordResetRequestDTO missing = request("Correct-Horse-Battery-2026", null);

        assertThat(validator.validate(different)).extracting("message")
                .contains("两次输入的新密码不一致");
        assertThat(validator.validate(missing)).extracting("message")
                .contains("确认密码不能为空", "两次输入的新密码不一致");
    }

    private PasswordResetRequestDTO request(String password, String confirmation) {
        PasswordResetRequestDTO request = new PasswordResetRequestDTO();
        request.setNewPassword(password);
        request.setConfirmPassword(confirmation);
        return request;
    }
}
