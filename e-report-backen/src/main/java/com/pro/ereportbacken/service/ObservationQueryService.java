package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.ObservationEventDetailDTO;
import com.pro.ereportbacken.model.dto.ObservationEventSummaryDTO;
import com.pro.ereportbacken.model.dto.ObservationUserOptionDTO;
import com.pro.ereportbacken.model.pojo.ClientObservationEvent;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.ObservationViewMode;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.ClientObservationEventRepository;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ObservationQueryService {

    private static final int DEFAULT_LOOKBACK_DAYS = 7;
    private static final int MAX_QUERY_DAYS = 31;

    private final ClientObservationEventRepository eventRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;
    private final ObjectMapper objectMapper;

    public ObservationQueryService(ClientObservationEventRepository eventRepository,
                                   OrgUnitRepository orgUnitRepository,
                                   UserRepository userRepository,
                                   TenantContext tenantContext,
                                   OrganizationScopeAuthorizationService scopeAuthorizationService,
                                   ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.scopeAuthorizationService = scopeAuthorizationService;
        this.objectMapper = objectMapper;
    }

    public Page<ObservationEventSummaryDTO> search(ObservationPlatform platform,
                                                   ObservationEventType eventType,
                                                   ObservationSeverity severity,
                                                   ObservationViewMode view,
                                                   String category,
                                                   String keyword,
                                                   UUID orgUnitId,
                                                   UUID userId,
                                                   Instant from,
                                                   Instant to,
                                                   Pageable pageable) {
        UUID tenantId = tenantContext.requireTenantId();
        OrganizationScopeAuthorizationService.OrganizationAccess access =
                scopeAuthorizationService.accessFor(PermissionCatalog.OBSERVABILITY_VIEW);
        requireSelectedOrganization(access, orgUnitId);
        if (!access.tenantWide() && access.orgUnitIds().isEmpty()) {
            return Page.empty(pageable);
        }

        TimeRange range = normalizeRange(from, to);
        Pageable safePageable = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.max(1, Math.min(pageable.getPageSize(), 100)),
                Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id"))
        );
        Specification<ClientObservationEvent> specification = specification(
                tenantId, access, platform, eventType, severity, view, category, keyword, orgUnitId, userId, range
        );
        Page<ClientObservationEvent> page = eventRepository.findAll(specification, safePageable);
        NameLookup names = loadNames(page.getContent());
        List<ObservationEventSummaryDTO> content = page.getContent().stream()
                .map(event -> toSummary(event, names))
                .toList();
        return new PageImpl<>(content, safePageable, page.getTotalElements());
    }

    public ObservationEventDetailDTO findById(UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        OrganizationScopeAuthorizationService.OrganizationAccess access =
                scopeAuthorizationService.accessFor(PermissionCatalog.OBSERVABILITY_VIEW);
        ClientObservationEvent event = eventRepository.findById(id)
                .filter(candidate -> candidate.getTenantId().equals(tenantId))
                .filter(candidate -> access.tenantWide()
                        || (candidate.getOrgUnitId() != null && access.orgUnitIds().contains(candidate.getOrgUnitId())))
                .orElseThrow(() -> new EntityNotFoundException("运行诊断事件不存在或当前账号无权查看。"));
        NameLookup names = loadNames(List.of(event));
        return toDetail(event, names);
    }

    public List<ObservationUserOptionDTO> searchUsers(String keyword, int limit) {
        UUID tenantId = tenantContext.requireTenantId();
        OrganizationScopeAuthorizationService.OrganizationAccess access =
                scopeAuthorizationService.accessFor(PermissionCatalog.OBSERVABILITY_VIEW);
        if (!access.tenantWide() && access.orgUnitIds().isEmpty()) {
            return List.of();
        }
        String normalizedKeyword = StringUtils.hasText(keyword)
                ? keyword.trim().toLowerCase(Locale.ROOT)
                : "";
        int safeLimit = Math.max(1, Math.min(limit, 20));
        Specification<User> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId));
            if (!access.tenantWide()) {
                predicates.add(root.get("orgUnitId").in(access.orgUnitIds()));
            }
            if (!normalizedKeyword.isEmpty()) {
                String pattern = "%" + escapeLike(normalizedKeyword) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("namePinyin")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("namePinyinInitials")), pattern, '\\')
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        List<User> users = userRepository.findAll(
                specification,
                PageRequest.of(0, safeLimit, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("username")))
        ).getContent();
        Set<UUID> orgIds = users.stream().map(User::getOrgUnitId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> orgNames = orgUnitRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(OrgUnit::getId, OrgUnit::getName));
        return users.stream().map(user -> new ObservationUserOptionDTO(
                user.getId(), user.getName(), displayUsername(user.getUsername()), user.getOrgUnitId(),
                nameOf(orgNames, user.getOrgUnitId())
        )).toList();
    }

    private String displayUsername(String username) {
        if (username != null && username.matches("^1[3-9]\\d{9}$")) {
            return username.substring(0, 3) + "****" + username.substring(7);
        }
        return username;
    }

    private Specification<ClientObservationEvent> specification(
            UUID tenantId,
            OrganizationScopeAuthorizationService.OrganizationAccess access,
            ObservationPlatform platform,
            ObservationEventType eventType,
            ObservationSeverity severity,
            ObservationViewMode view,
            String category,
            String keyword,
            UUID orgUnitId,
            UUID userId,
            TimeRange range) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId));
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("receivedAt"), range.from()));
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("receivedAt"), range.to()));
            ObservationViewMode effectiveView = view == null ? ObservationViewMode.DIAGNOSTIC : view;
            if (effectiveView == ObservationViewMode.BEHAVIOR) {
                predicates.add(criteriaBuilder.equal(root.get("eventType"), ObservationEventType.OPERATION));
            } else {
                predicates.add(criteriaBuilder.not(criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("eventType"), ObservationEventType.OPERATION),
                        criteriaBuilder.equal(root.get("severity"), ObservationSeverity.INFO)
                )));
            }
            if (orgUnitId != null) {
                predicates.add(criteriaBuilder.equal(root.get("orgUnitId"), orgUnitId));
            } else if (!access.tenantWide()) {
                predicates.add(root.get("orgUnitId").in(access.orgUnitIds()));
            }
            if (platform != null) {
                predicates.add(criteriaBuilder.equal(root.get("platform"), platform));
            }
            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            }
            if (eventType != null) {
                predicates.add(criteriaBuilder.equal(root.get("eventType"), eventType));
            }
            if (severity != null) {
                predicates.add(criteriaBuilder.equal(root.get("severity"), severity));
            }
            if (StringUtils.hasText(category)) {
                predicates.add(criteriaBuilder.equal(root.get("category"), category.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("eventName")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("message")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("route")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("traceId")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("otelTraceId")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("requestId")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("appVersion")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("deviceModel")), pattern, '\\')
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void requireSelectedOrganization(OrganizationScopeAuthorizationService.OrganizationAccess access,
                                             UUID orgUnitId) {
        if (orgUnitId != null && !access.orgUnitIds().contains(orgUnitId)) {
            throw new AccessDeniedException("当前账号无权查看所选组织的运行诊断。");
        }
    }

    private TimeRange normalizeRange(Instant from, Instant to) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime normalizedTo = to == null ? now : LocalDateTime.ofInstant(to, ZoneOffset.UTC);
        LocalDateTime normalizedFrom = from == null
                ? normalizedTo.minusDays(DEFAULT_LOOKBACK_DAYS)
                : LocalDateTime.ofInstant(from, ZoneOffset.UTC);
        if (normalizedFrom.isAfter(normalizedTo)) {
            throw new IllegalArgumentException("查询开始时间不能晚于结束时间。");
        }
        if (normalizedFrom.isBefore(normalizedTo.minusDays(MAX_QUERY_DAYS))) {
            throw new IllegalArgumentException("单次运行诊断查询时间范围不能超过31天。");
        }
        return new TimeRange(normalizedFrom, normalizedTo);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private NameLookup loadNames(List<ClientObservationEvent> events) {
        Set<UUID> orgIds = events.stream().map(ClientObservationEvent::getOrgUnitId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> userIds = events.stream().map(ClientObservationEvent::getUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> orgNames = orgUnitRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(OrgUnit::getId, OrgUnit::getName));
        Map<UUID, String> userNames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        return new NameLookup(orgNames, userNames);
    }

    private ObservationEventSummaryDTO toSummary(ClientObservationEvent event, NameLookup names) {
        return new ObservationEventSummaryDTO(
                event.getId(), event.getPlatform(), event.getEventType(), event.getSeverity(), event.getCategory(),
                event.getEventName(), event.getMessage(), event.getRoute(), event.getOperationName(), event.getOutcome(),
                event.getTraceId(), event.getObservationSchemaVersion(), event.getSignalType(), event.getRequestId(),
                event.getOtelTraceId(), event.getOtelSpanId(), event.getSeverityNumber(), event.getSpanName(),
                event.getDurationNanos(), event.getOrgUnitId(), nameOf(names.orgNames(), event.getOrgUnitId()), event.getUserId(),
                nameOf(names.userNames(), event.getUserId()), event.getAppVersion(), event.getOsName(), event.getOsVersion(),
                event.getManufacturer(), event.getDeviceModel(), event.getNetworkType(), toInstant(event.getClientOccurredAt()),
                toInstant(event.getReceivedAt())
        );
    }

    private ObservationEventDetailDTO toDetail(ClientObservationEvent event, NameLookup names) {
        return new ObservationEventDetailDTO(
                event.getId(), event.getClientEventId(), event.getPlatform(), event.getEventType(), event.getSeverity(),
                event.getCategory(), event.getEventName(), event.getMessage(), event.getStackTrace(), event.getRoute(),
                event.getOperationName(), event.getOutcome(), event.getTraceId(),
                event.getObservationSchemaVersion(), event.getSignalType(), event.getRequestId(), event.getOtelTraceId(),
                event.getOtelSpanId(), event.getOtelParentSpanId(), event.getOtelTraceFlags(), event.getSeverityNumber(),
                event.getSeverityText(), event.getBody(), event.getScopeName(), event.getScopeVersion(),
                parseJson(event.getResourceJson()), parseJson(event.getScopeAttributesJson()), parseJson(event.getAttributesJson()),
                event.getSpanName(), event.getSpanKind(), event.getSpanStatusCode(), event.getSpanStatusMessage(),
                event.getDurationNanos(), parseJson(event.getSpanEventsJson()), event.getOrgUnitId(),
                nameOf(names.orgNames(), event.getOrgUnitId()), event.getUserId(), nameOf(names.userNames(), event.getUserId()),
                event.getInstallationId(), event.getSessionId(), event.getAppVersion(), event.getAppBuild(),
                event.getOsName(), event.getOsVersion(), event.getManufacturer(), event.getDeviceModel(),
                event.getUserAgent(), event.getNetworkType(), parseJson(event.getContextJson()),
                parseJson(event.getBreadcrumbsJson()), toInstant(event.getClientOccurredAt()), toInstant(event.getReceivedAt())
        );
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    private Object parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record TimeRange(LocalDateTime from, LocalDateTime to) {
    }

    private record NameLookup(Map<UUID, String> orgNames, Map<UUID, String> userNames) {
        private NameLookup {
            orgNames = Map.copyOf(new HashMap<>(orgNames));
            userNames = Map.copyOf(new HashMap<>(userNames));
        }
    }
}
