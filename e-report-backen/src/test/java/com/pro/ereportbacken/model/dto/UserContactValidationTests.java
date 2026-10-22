package com.pro.ereportbacken.model.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContactValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresPhoneWhenCreatingOrEditingUsers() {
        UserCreateRequestDTO create = new UserCreateRequestDTO();
        create.setUsername("new-user");
        create.setPassword("valid-random-phrase-2026");
        create.setName("新用户");

        UserUpdateRequestDTO update = new UserUpdateRequestDTO();
        update.setName("更新用户");
        update.setPhone("05961234567");

        CurrentUserUpdateRequestDTO current = new CurrentUserUpdateRequestDTO();
        current.setName("当前用户");

        assertThat(validator.validate(create)).anyMatch(violation ->
                violation.getPropertyPath().toString().equals("phone")
                        && violation.getMessage().contains("不能为空"));
        assertThat(validator.validate(update)).anyMatch(violation ->
                violation.getPropertyPath().toString().equals("phone")
                        && violation.getMessage().contains("11位"));
        assertThat(validator.validate(current)).anyMatch(violation ->
                violation.getPropertyPath().toString().equals("phone"));
    }

    @Test
    void acceptsOptionalEmailAndRejectsMalformedEmail() {
        CurrentUserUpdateRequestDTO request = new CurrentUserUpdateRequestDTO();
        request.setName("当前用户");
        request.setPhone("13900000000");

        assertThat(validator.validate(request)).isEmpty();

        request.setEmail("invalid-address");
        assertThat(validator.validate(request)).anyMatch(violation ->
                violation.getPropertyPath().toString().equals("email"));

        request.setEmail("user@example.com");
        assertThat(validator.validate(request)).isEmpty();
    }
}
