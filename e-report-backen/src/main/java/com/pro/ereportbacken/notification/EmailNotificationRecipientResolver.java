package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.UserNotificationContact;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import com.pro.ereportbacken.service.UserContactPolicy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Service
@Log4j2
public class EmailNotificationRecipientResolver {

    private final TenantContext tenantContext;
    private final OrganizationHierarchyService hierarchyService;
    private final UserRepository userRepository;
    private final PermissionGrantRepository permissionGrantRepository;
    private final Set<UUID> recipientAllowlistUserIds;

    public EmailNotificationRecipientResolver(TenantContext tenantContext,
                                              OrganizationHierarchyService hierarchyService,
                                              UserRepository userRepository,
                                              PermissionGrantRepository permissionGrantRepository,
                                              @Value("${app.notification.email.recipient-allowlist-user-ids:}") String recipientAllowlistUserIds) {
        this.tenantContext = tenantContext;
        this.hierarchyService = hierarchyService;
        this.userRepository = userRepository;
        this.permissionGrantRepository = permissionGrantRepository;
        this.recipientAllowlistUserIds = parseRecipientAllowlist(recipientAllowlistUserIds);
    }

    public RecipientSelection resolve(WorkOrderNotification notification) {
        if (notification.isAssignmentReceived()) {
            WorkOrderNotification.AssigneeContact contact = notification.assignee();
            if (contact == null || !allowedRecipient(contact.userId())) {
                return new RecipientSelection(List.of(), 0, 0);
            }
            if (!contact.emailSubscribed()) return new RecipientSelection(List.of(), 0, 1);
            EmailRecipient recipient = toRecipient(contact);
            return new RecipientSelection(recipient == null ? List.of() : List.of(recipient), 1, 0);
        }
        EmailNotificationCategory category = notification.isSlaBreach()
                ? EmailNotificationCategory.SLA_BREACHED : EmailNotificationCategory.CREATED;
        return resolve(tenantContext.requireTenantId(), notification.reportingOrgUnitId(), category);
    }

    public RecipientSelection resolve(UUID tenantId, UUID reportingOrgUnitId, EmailNotificationCategory category) {
        List<UserNotificationContact> contacts = hierarchyService.nearestAncestorOfType(
                        tenantId,
                        reportingOrgUnitId,
                        OrgUnitType.COUNTY)
                .map(county -> countyContacts(tenantId, county))
                .orElseGet(() -> tenantContacts(tenantId));

        Map<String, EmailRecipient> recipientsByAddress = new LinkedHashMap<>();
        int subscribedCount = 0;
        int unsubscribedCount = 0;
        for (UserNotificationContact contact : contacts) {
            if (!allowedRecipient(contact.getUserId())) continue;
            if (!subscribed(contact, category)) {
                unsubscribedCount++;
                continue;
            }
            subscribedCount++;
            EmailRecipient recipient = toRecipient(contact);
            if (recipient == null) {
                continue;
            }
            EmailRecipient duplicate = recipientsByAddress.putIfAbsent(
                    recipient.address().toLowerCase(Locale.ROOT), recipient);
            if (duplicate != null) {
                log.info("通知收件地址重复，已合并。userId={}, existingUserId={}, address={}",
                        recipient.userId(), duplicate.userId(), maskAddress(recipient.address()));
            }
        }
        return new RecipientSelection(List.copyOf(recipientsByAddress.values()), subscribedCount, unsubscribedCount);
    }

    private boolean allowedRecipient(UUID userId) {
        return recipientAllowlistUserIds.isEmpty() || recipientAllowlistUserIds.contains(userId);
    }

    private boolean subscribed(UserNotificationContact contact, EmailNotificationCategory category) {
        return switch (category) {
            case CREATED -> contact.getEmailWorkOrderCreated();
            case SLA_BREACHED -> contact.getEmailWorkOrderSlaBreached();
            case ASSIGNMENT_RECEIVED -> contact.getEmailWorkOrderAssigned();
            case DAILY_OUTSTANDING_REPORT -> contact.getEmailDailyOutstandingReport();
        };
    }

    private Set<UUID> parseRecipientAllowlist(String configuredUserIds) {
        if (configuredUserIds == null || configuredUserIds.isBlank()) {
            return Set.of();
        }
        try {
            return java.util.Arrays.stream(configuredUserIds.split(",", -1))
                    .map(String::trim)
                    .peek(value -> {
                        if (value.isEmpty()) {
                            throw new IllegalArgumentException("邮件收件人白名单不能包含空用户 ID。");
                        }
                    })
                    .map(UUID::fromString)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "邮件收件人白名单必须是逗号分隔的 UUID。", exception);
        }
    }

    private List<UserNotificationContact> countyContacts(UUID tenantId, OrgUnitDTO county) {
        Set<UUID> membershipIds = activeRecipients(
                tenantId, PermissionCatalog.NOTIFICATION_RECIPIENT_COUNTY);
        if (membershipIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findNotificationContactsByMembershipsAndOrganizations(
                tenantId,
                UserStatus.ACTIVE,
                membershipIds,
                hierarchyService.descendantIds(tenantId, county.id()));
    }

    private List<UserNotificationContact> tenantContacts(UUID tenantId) {
        Set<UUID> membershipIds = activeRecipients(
                tenantId, PermissionCatalog.NOTIFICATION_RECIPIENT_TENANT);
        if (membershipIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findNotificationContactsByMemberships(
                tenantId, UserStatus.ACTIVE, membershipIds);
    }

    private Set<UUID> activeRecipients(UUID tenantId, String permissionName) {
        return Set.copyOf(permissionGrantRepository.findActiveMembershipIdsByPermission(
                tenantId, permissionName, UtcTime.now()));
    }

    private EmailRecipient toRecipient(UserNotificationContact contact) {
        return toRecipient(contact.getUserId(), contact.getEmail(), contact.getPhone());
    }

    private EmailRecipient toRecipient(WorkOrderNotification.AssigneeContact contact) {
        return contact == null ? null : toRecipient(contact.userId(), contact.email(), contact.phone());
    }

    private EmailRecipient toRecipient(UUID userId, String email, String phone) {
        if (UserContactPolicy.isValidEmail(email)) {
            return new EmailRecipient(userId, email.trim(), false);
        }
        if (email != null && !email.isBlank()) {
            log.warn("用户通知邮箱格式无效，将尝试手机号回退。userId={}, email={}",
                    userId, maskAddress(email));
        }
        if (UserContactPolicy.isValidPhone(phone)) {
            return new EmailRecipient(userId, phone.trim() + "@139.com", true);
        }
        log.warn("用户缺少可用通知联系方式，已跳过。userId={}, phone={}", userId, maskPhone(phone));
        return null;
    }

    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return "<empty>";
        }
        int at = address.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String localPart = address.substring(0, at);
        String visible = localPart.length() <= 2 ? localPart.substring(0, 1) : localPart.substring(0, 2);
        return visible + "***" + address.substring(at);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "<empty>";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public record EmailRecipient(UUID userId, String address, boolean phoneFallback) {
    }

    public record RecipientSelection(List<EmailRecipient> recipients, int subscribedCount, int unsubscribedCount) {
        public RecipientSelection {
            recipients = List.copyOf(recipients);
        }

        public boolean allUnsubscribed() {
            return subscribedCount == 0 && unsubscribedCount > 0;
        }
    }
}
