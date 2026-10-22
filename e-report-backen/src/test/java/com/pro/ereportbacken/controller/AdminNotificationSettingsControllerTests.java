package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.MailSenderTestRequestDTO;
import com.pro.ereportbacken.model.dto.MailSenderTestResultDTO;
import com.pro.ereportbacken.notification.MailSenderConfigurationService;
import com.pro.ereportbacken.notification.MailSenderTestService;
import com.pro.ereportbacken.notification.NotificationSettingsService;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.OrgUnitService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminNotificationSettingsControllerTests {

    @Test
    void testMailRequiresSenderUpdatePermissionAndOrganizationScope() throws Exception {
        MailSenderTestService testService = mock(MailSenderTestService.class);
        OrganizationScopeAuthorizationService scopeAuthorization = mock(OrganizationScopeAuthorizationService.class);
        AdminNotificationSettingsController controller = new AdminNotificationSettingsController(
                mock(NotificationSettingsService.class), mock(MailSenderConfigurationService.class), testService,
                scopeAuthorization, mock(OrgUnitService.class));
        UUID orgUnitId = UUID.randomUUID();
        MailSenderTestRequestDTO request = new MailSenderTestRequestDTO(null, "test@example.com", null);
        when(testService.send(request, orgUnitId)).thenReturn(
                new MailSenderTestResultDTO(true, "SMTP服务器已接受测试邮件。", null));

        var response = controller.testSender(request, orgUnitId);

        assertThat(response.getBody()).isEqualTo(new MailSenderTestResultDTO(
                true, "SMTP服务器已接受测试邮件。", null));
        verify(scopeAuthorization).requireOrganizationAccess(
                PermissionCatalog.NOTIFICATION_SENDER_UPDATE, orgUnitId);
        verify(testService).send(request, orgUnitId);

        Method method = AdminNotificationSettingsController.class.getMethod(
                "testSender", MailSenderTestRequestDTO.class, UUID.class);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/sender/test");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .contains(PermissionCatalog.NOTIFICATION_SENDER_UPDATE);
    }

    @Test
    void rejectsTestSendBeforeSendingWhenScopeAuthorizationFails() {
        MailSenderTestService testService = mock(MailSenderTestService.class);
        OrganizationScopeAuthorizationService scopeAuthorization = mock(OrganizationScopeAuthorizationService.class);
        AdminNotificationSettingsController controller = new AdminNotificationSettingsController(
                mock(NotificationSettingsService.class), mock(MailSenderConfigurationService.class), testService,
                scopeAuthorization, mock(OrgUnitService.class));
        UUID orgUnitId = UUID.randomUUID();
        MailSenderTestRequestDTO request = new MailSenderTestRequestDTO(null, "test@example.com", null);
        doThrow(new AccessDeniedException("forbidden"))
                .when(scopeAuthorization).requireOrganizationAccess(
                        PermissionCatalog.NOTIFICATION_SENDER_UPDATE, orgUnitId);

        assertThatThrownBy(() -> controller.testSender(request, orgUnitId))
                .isInstanceOf(AccessDeniedException.class);
        verify(testService, never()).send(request, orgUnitId);
    }
}
