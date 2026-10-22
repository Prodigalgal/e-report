package com.pro.ereportbacken.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderControllerRouteTests {

    @Test
    void workOrderRoutesDoNotRetainNumericIdentifierConstraints() {
        assertThat(Arrays.stream(WorkOrderController.class.getDeclaredMethods())
                .map(this::requestMapping)
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.path().length == 0 ? mapping.value() : mapping.path())))
                .noneMatch(path -> path.contains("\\d+"));
    }

    @Test
    void attachmentDownloadUsesAnOpaqueAttachmentUuidPath() {
        assertThat(Arrays.stream(WorkOrderController.class.getDeclaredMethods())
                .map(this::requestMapping)
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.path().length == 0 ? mapping.value() : mapping.path())))
                .contains("/attachments/{workOrderId}/file/{attachmentId}");
    }

    @Test
    void identifierPathVariablesUseUuidTypes() {
        assertThat(Arrays.stream(WorkOrderController.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .filter(this::isIdentifierPathVariable)
                .map(Parameter::getType)
                .toList())
                .hasSize(20)
                .allMatch(UUID.class::equals);
    }

    private RequestMapping requestMapping(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
    }

    private boolean isIdentifierPathVariable(Parameter parameter) {
        PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
        if (pathVariable == null) {
            return false;
        }
        String name = pathVariable.name().isBlank() ? pathVariable.value() : pathVariable.name();
        if (name.isBlank()) {
            name = parameter.getName();
        }
        return name.toLowerCase().endsWith("id");
    }
}
