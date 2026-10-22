package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.model.dto.EmailDeliveryDTO;
import com.pro.ereportbacken.model.pojo.EmailDelivery;
import com.pro.ereportbacken.repository.EmailDeliveryRepository;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.time.DisplayTime;
import com.pro.ereportbacken.time.UtcTime;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EmailDeliveryQueryService {
    private static final int MAX_QUERY_DAYS = 93;
    private static final int SENDING_TIMEOUT_MINUTES = 15;
    private final EmailDeliveryRepository repository;
    private final OrganizationHierarchyService organizations;
    private final TenantContext tenantContext;
    private final OrganizationScopeAuthorizationService authorization;

    public EmailDeliveryQueryService(EmailDeliveryRepository repository, OrganizationHierarchyService organizations,
                                     TenantContext tenantContext, OrganizationScopeAuthorizationService authorization) {
        this.repository = repository;
        this.organizations = organizations;
        this.tenantContext = tenantContext;
        this.authorization = authorization;
    }

    public Page<EmailDeliveryDTO> search(EmailDeliveryCategory category, EmailDeliveryStatus status,
                                          String keyword, UUID orgUnitId, LocalDate from, LocalDate to,
                                          Pageable pageable) {
        UUID tenantId = tenantContext.requireTenantId();
        var access = authorization.accessFor(PermissionCatalog.EMAIL_DELIVERY_VIEW);
        if (orgUnitId != null && !access.orgUnitIds().contains(orgUnitId)) {
            throw new AccessDeniedException("无权查询目标组织的邮件发送记录。");
        }
        LocalDate endDate = to == null ? LocalDate.now(DisplayTime.ZONE_ID) : to;
        LocalDate startDate = from == null ? endDate.minusDays(6) : from;
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (days < 0 || days >= MAX_QUERY_DAYS) {
            throw new IllegalArgumentException("查询日期顺序不正确或跨度超过 93 天。");
        }
        LocalDateTime fromUtc;
        LocalDateTime untilUtc;
        try {
            fromUtc = DisplayTime.startOfDisplayDateUtc(startDate);
            untilUtc = DisplayTime.startOfDisplayDateUtc(endDate.plusDays(1));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("查询日期超出支持范围。", exception);
        }
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 160) throw new IllegalArgumentException("关键词不能超过 160 个字符。");
        LocalDateTime cutoff = UtcTime.now().minusMinutes(SENDING_TIMEOUT_MINUTES);
        Specification<EmailDelivery> specification = visibility(tenantId, access).and((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            filters.add(cb.greaterThanOrEqualTo(root.get("startedAt"), fromUtc));
            filters.add(cb.lessThan(root.get("startedAt"), untilUtc));
            if (category != null) filters.add(cb.equal(root.get("category"), category));
            if (orgUnitId != null) filters.add(cb.equal(root.get("orgUnitId"), orgUnitId));
            if (status == EmailDeliveryStatus.FAILED) {
                filters.add(cb.or(cb.equal(root.get("status"), EmailDeliveryStatus.FAILED), cb.and(
                        cb.equal(root.get("status"), EmailDeliveryStatus.SENDING), cb.lessThan(root.get("startedAt"), cutoff))));
            } else if (status == EmailDeliveryStatus.SENDING) {
                filters.add(cb.and(cb.equal(root.get("status"), status), cb.greaterThanOrEqualTo(root.get("startedAt"), cutoff)));
            } else if (status != null) filters.add(cb.equal(root.get("status"), status));
            if (!normalized.isEmpty()) {
                String pattern = "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                List<Predicate> matches = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("subject")), pattern, '\\'),
                        cb.like(cb.lower(root.get("recipientAddress")), pattern, '\\'),
                        cb.like(cb.lower(root.get("senderAddress")), pattern, '\\')));
                if (normalized.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
                    UUID identifier = UUID.fromString(normalized);
                    matches.add(cb.equal(root.get("id"), identifier));
                    matches.add(cb.equal(root.get("relatedId"), identifier));
                }
                filters.add(cb.or(matches.toArray(Predicate[]::new)));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        });
        Pageable safePage = PageRequest.of(Math.max(0, pageable.getPageNumber()),
                Math.min(100, Math.max(1, pageable.getPageSize())), Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        Page<EmailDelivery> page = repository.findAll(specification, safePage);
        Map<UUID, String> names = organizationNames(tenantId, page.getContent().stream()
                .map(EmailDelivery::getOrgUnitId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return page.map(delivery -> toDto(delivery, names, cutoff));
    }

    public EmailDeliveryDTO detail(UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        var access = authorization.accessFor(PermissionCatalog.EMAIL_DELIVERY_VIEW);
        EmailDelivery delivery = repository.findOne(visibility(tenantId, access)
                .and((root, query, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new EntityNotFoundException("邮件发送记录不存在或无权查看。"));
        Map<UUID, String> names = organizationNames(tenantId,
                delivery.getOrgUnitId() == null ? Set.of() : Set.of(delivery.getOrgUnitId()));
        return toDto(delivery, names, UtcTime.now().minusMinutes(SENDING_TIMEOUT_MINUTES));
    }

    public List<OrganizationOption> scopes() {
        UUID tenantId = tenantContext.requireTenantId();
        return organizationNames(tenantId, authorization.accessFor(PermissionCatalog.EMAIL_DELIVERY_VIEW).orgUnitIds())
                .entrySet().stream().map(entry -> new OrganizationOption(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(OrganizationOption::name)).toList();
    }

    private Specification<EmailDelivery> visibility(UUID tenantId, OrganizationScopeAuthorizationService.OrganizationAccess access) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("tenantId"), tenantId), access.tenantWide()
                ? cb.conjunction() : access.orgUnitIds().isEmpty() ? cb.disjunction() : root.get("orgUnitId").in(access.orgUnitIds()));
    }

    private Map<UUID, String> organizationNames(UUID tenantId, Set<UUID> ids) {
        return organizations.namesById(tenantId, ids);
    }

    private EmailDeliveryDTO toDto(EmailDelivery delivery, Map<UUID, String> names, LocalDateTime cutoff) {
        boolean incomplete = delivery.getStatus() == EmailDeliveryStatus.SENDING && delivery.getStartedAt().isBefore(cutoff);
        EmailDeliveryStatus status = incomplete ? EmailDeliveryStatus.FAILED : delivery.getStatus();
        String failureCode = incomplete ? EmailDeliveryFailure.INCOMPLETE.name() : delivery.getFailureCode();
        String reason = null;
        if (failureCode != null) {
            try { reason = EmailDeliveryFailure.valueOf(failureCode).message(); }
            catch (IllegalArgumentException exception) { reason = EmailDeliveryFailure.DELIVERY.message(); }
        }
        return new EmailDeliveryDTO(delivery.getId(), delivery.getOrgUnitId(), delivery.getOrgUnitId() == null
                ? "市公司级" : names.getOrDefault(delivery.getOrgUnitId(), "组织已移除"), delivery.getCategory(), status,
                delivery.getSubject(), delivery.getRecipientAddress(), delivery.getSenderAddress(), delivery.getRelatedId(),
                delivery.getRecipientUserId(), delivery.getStartedAt(), delivery.getCompletedAt(), delivery.getDurationMs(),
                failureCode, reason);
    }

    public record OrganizationOption(UUID id, String name) {
    }
}
