package com.pro.ereportbacken.specification;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.time.DisplayTime;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkOrderSpecification {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern WORK_ORDER_ID_PATTERN = Pattern.compile(
            "^(?:#|工单\\s*#?)?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$"
    );

    public static Specification<WorkOrder> getWorkOrdersByCriteria(WorkOrderFilterDTO filterDTO) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filterDTO == null) {
                return criteriaBuilder.conjunction();
            }

            if (filterDTO.getTenantId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), filterDTO.getTenantId()));
            }

            Predicate accessPredicate = buildAccessPredicate(root, query, criteriaBuilder, filterDTO);
            if (accessPredicate != null) {
                predicates.add(accessPredicate);
            }

            if (filterDTO.getReportingOrgUnitIds() != null) {
                predicates.add(filterDTO.getReportingOrgUnitIds().isEmpty()
                        ? criteriaBuilder.disjunction()
                        : root.get("reportingOrgId").in(filterDTO.getReportingOrgUnitIds()));
            }

            if (filterDTO.getSelectedWorkOrderIds() != null) {
                predicates.add(filterDTO.getSelectedWorkOrderIds().isEmpty()
                        ? criteriaBuilder.disjunction()
                        : root.get("id").in(filterDTO.getSelectedWorkOrderIds()));
            }

            Predicate queuePredicate = buildQueuePredicate(root, query, criteriaBuilder, filterDTO);
            if (queuePredicate != null) {
                predicates.add(queuePredicate);
            }

            if (StringUtils.hasText(filterDTO.getSearchTerm())) {
                predicates.add(buildKeywordPredicate(root, criteriaBuilder, filterDTO.getSearchTerm()));
            }

            if (StringUtils.hasText(filterDTO.getLocation())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("location")),
                        "%" + filterDTO.getLocation().toLowerCase() + "%"));
            }
            Predicate statusPredicate = buildStatusPredicate(root, criteriaBuilder, filterDTO);
            if (statusPredicate != null) {
                predicates.add(statusPredicate);
            }
            if (StringUtils.hasText(filterDTO.getContactPerson())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("contactPerson")),
                        "%" + filterDTO.getContactPerson().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(filterDTO.getPhone())) {
                predicates.add(criteriaBuilder.like(root.get("phone"), "%" + filterDTO.getPhone() + "%"));
            }
            if (StringUtils.hasText(filterDTO.getReason())) {
                predicates.add(containsLob(root, criteriaBuilder, "reason",
                        "%" + escapeLike(filterDTO.getReason().trim()) + "%"));
            }
            Predicate reportTypePredicate = buildReportTypePredicate(root, criteriaBuilder, filterDTO);
            if (reportTypePredicate != null) {
                predicates.add(reportTypePredicate);
            }

            LocalDate dateFrom = StringUtils.hasText(filterDTO.getReportDateFrom())
                    ? parseReportDate(filterDTO.getReportDateFrom(), "reportDateFrom")
                    : null;
            LocalDate dateTo = StringUtils.hasText(filterDTO.getReportDateTo())
                    ? parseReportDate(filterDTO.getReportDateTo(), "reportDateTo")
                    : null;
            if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
                throw new IllegalArgumentException("报单开始日期不能晚于结束日期。" );
            }
            if (dateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("reportTime"), DisplayTime.startOfDisplayDateUtc(dateFrom)));
            }
            if (dateTo != null) {
                predicates.add(criteriaBuilder.lessThan(
                        root.get("reportTime"), DisplayTime.startOfDisplayDateUtc(dateTo.plusDays(1))));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate buildAccessPredicate(Root<WorkOrder> root,
                                                   CriteriaQuery<?> query,
                                                   CriteriaBuilder criteriaBuilder,
                                                   WorkOrderFilterDTO filterDTO) {
        List<Predicate> accessPredicates = new ArrayList<>();
        UUID membershipId = filterDTO.getAccessMembershipId();
        if (membershipId != null && filterDTO.getTenantWideReporterTypes() != null
                && !filterDTO.getTenantWideReporterTypes().isEmpty()) {
            accessPredicates.add(criteriaBuilder.and(
                    root.get("reportType").in(filterDTO.getTenantWideReporterTypes()),
                    criteriaBuilder.equal(root.get("reportedBy").get("id"), membershipId)
            ));
        }
        addRelationshipOrganizationPredicates(
                root, criteriaBuilder, accessPredicates, filterDTO.getReporterOrgUnitIdsByType(),
                criteriaBuilder.equal(root.get("reportedBy").get("id"), membershipId));
        if (membershipId != null && filterDTO.getTenantWideParticipantTypes() != null
                && !filterDTO.getTenantWideParticipantTypes().isEmpty()) {
            accessPredicates.add(criteriaBuilder.and(
                    root.get("reportType").in(filterDTO.getTenantWideParticipantTypes()),
                    buildParticipantPredicate(root, query, criteriaBuilder, filterDTO.getTenantId(), membershipId)
            ));
        }
        if (membershipId != null && filterDTO.getParticipantOrgUnitIdsByType() != null) {
            Predicate participant = buildParticipantPredicate(
                    root, query, criteriaBuilder, filterDTO.getTenantId(), membershipId);
            addRelationshipOrganizationPredicates(
                    root, criteriaBuilder, accessPredicates,
                    filterDTO.getParticipantOrgUnitIdsByType(), participant);
        }
        if (filterDTO.getAccessibleReportTypes() != null && !filterDTO.getAccessibleReportTypes().isEmpty()) {
            accessPredicates.add(root.get("reportType").in(filterDTO.getAccessibleReportTypes()));
        }
        if (filterDTO.getAccessibleOrgUnitIdsByType() != null) {
            filterDTO.getAccessibleOrgUnitIdsByType().forEach((type, orgUnitIds) -> {
                if (orgUnitIds != null && !orgUnitIds.isEmpty()) {
                    accessPredicates.add(criteriaBuilder.and(
                            criteriaBuilder.equal(root.get("reportType"), type),
                            buildOrganizationVisibilityPredicate(
                                    root, query, criteriaBuilder, filterDTO.getTenantId(), orgUnitIds)
                    ));
                }
            });
        }
        Predicate claimable = buildClaimablePredicate(root, query, criteriaBuilder);
        if (filterDTO.getTenantWideClaimableTypes() != null && !filterDTO.getTenantWideClaimableTypes().isEmpty()) {
            accessPredicates.add(criteriaBuilder.and(
                    root.get("reportType").in(filterDTO.getTenantWideClaimableTypes()),
                    claimable
            ));
        }
        if (filterDTO.getClaimableOrgUnitIdsByType() != null) {
            filterDTO.getClaimableOrgUnitIdsByType().forEach((type, orgUnitIds) -> {
                if (orgUnitIds != null && !orgUnitIds.isEmpty()) {
                    accessPredicates.add(criteriaBuilder.and(
                            criteriaBuilder.equal(root.get("reportType"), type),
                            root.get("responsibleOrgId").in(orgUnitIds),
                            claimable
                    ));
                }
            });
        }
        if (accessPredicates.isEmpty()) {
            return criteriaBuilder.disjunction();
        }
        return criteriaBuilder.or(accessPredicates.toArray(new Predicate[0]));
    }

    private static void addRelationshipOrganizationPredicates(
            Root<WorkOrder> root,
            CriteriaBuilder criteriaBuilder,
            List<Predicate> accessPredicates,
            java.util.Map<WorkOrderType, java.util.Set<UUID>> orgUnitIdsByType,
            Predicate relationshipPredicate) {
        if (relationshipPredicate == null || orgUnitIdsByType == null) {
            return;
        }
        orgUnitIdsByType.forEach((type, orgUnitIds) -> {
            if (orgUnitIds != null && !orgUnitIds.isEmpty()) {
                accessPredicates.add(criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("reportType"), type),
                        root.get("ownerOrgId").in(orgUnitIds),
                        relationshipPredicate
                ));
            }
        });
    }

    private static Predicate buildParticipantPredicate(Root<WorkOrder> root,
                                                       CriteriaQuery<?> query,
                                                       CriteriaBuilder criteriaBuilder,
                                                       UUID tenantId,
                                                       UUID membershipId) {
        Predicate pendingAssignee = buildAssignmentRelationshipPredicate(
                root, query, criteriaBuilder, tenantId, membershipId,
                com.pro.ereportbacken.model.pojo.AssignmentState.PENDING_ACCEPTANCE,
                com.pro.ereportbacken.model.pojo.AssignmentSlot.PENDING);
        Subquery<UUID> acceptedAssignment = query.subquery(UUID.class);
        Root<com.pro.ereportbacken.model.pojo.WorkOrderAssignment> assignment =
                acceptedAssignment.from(com.pro.ereportbacken.model.pojo.WorkOrderAssignment.class);
        acceptedAssignment.select(assignment.get("id"));
        acceptedAssignment.where(
                criteriaBuilder.equal(assignment.get("tenantId"), tenantId),
                criteriaBuilder.equal(assignment.get("workOrder").get("id"), root.get("id")),
                criteriaBuilder.equal(assignment.get("assignee").get("id"), membershipId),
                criteriaBuilder.isNotNull(assignment.get("acceptedAt"))
        );
        return criteriaBuilder.or(pendingAssignee, criteriaBuilder.exists(acceptedAssignment));
    }

    private static Predicate buildOrganizationVisibilityPredicate(
            Root<WorkOrder> root,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            UUID tenantId,
            java.util.Set<UUID> orgUnitIds) {
        Subquery<UUID> retainedOrganization = query.subquery(UUID.class);
        Root<com.pro.ereportbacken.model.pojo.WorkOrderOrganizationParticipation> participation =
                retainedOrganization.from(
                        com.pro.ereportbacken.model.pojo.WorkOrderOrganizationParticipation.class);
        retainedOrganization.select(participation.get("id"));
        retainedOrganization.where(
                criteriaBuilder.equal(participation.get("tenantId"), tenantId),
                criteriaBuilder.equal(participation.get("workOrderId"), root.get("id")),
                participation.get("orgUnitId").in(orgUnitIds),
                criteriaBuilder.isTrue(participation.get("active"))
        );
        return criteriaBuilder.or(
                root.get("ownerOrgId").in(orgUnitIds),
                criteriaBuilder.exists(retainedOrganization));
    }

    private static Predicate buildClaimablePredicate(Root<WorkOrder> root,
                                                     CriteriaQuery<?> query,
                                                     CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.and(
                root.get("statusCode").in(
                        com.pro.ereportbacken.model.pojo.WorkOrderStatus.SUBMITTED.getCode(),
                        com.pro.ereportbacken.model.pojo.WorkOrderStatus.IN_PROGRESS.getCode()
                ),
                buildNoAssignmentPredicate(root, query, criteriaBuilder,
                        com.pro.ereportbacken.model.pojo.AssignmentState.ACCEPTED,
                        com.pro.ereportbacken.model.pojo.AssignmentSlot.CURRENT),
                buildNoAssignmentPredicate(root, query, criteriaBuilder,
                        com.pro.ereportbacken.model.pojo.AssignmentState.PENDING_ACCEPTANCE,
                        com.pro.ereportbacken.model.pojo.AssignmentSlot.PENDING)
        );
    }

    private static Predicate buildAssignmentRelationshipPredicate(
            Root<WorkOrder> root,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            UUID tenantId,
            UUID membershipId,
            com.pro.ereportbacken.model.pojo.AssignmentState state,
            com.pro.ereportbacken.model.pojo.AssignmentSlot slot) {
        Subquery<UUID> assignmentSubquery = query.subquery(UUID.class);
        Root<com.pro.ereportbacken.model.pojo.WorkOrderAssignment> assignment =
                assignmentSubquery.from(com.pro.ereportbacken.model.pojo.WorkOrderAssignment.class);
        assignmentSubquery.select(assignment.get("id"));
        assignmentSubquery.where(
                criteriaBuilder.equal(assignment.get("tenantId"), tenantId),
                criteriaBuilder.equal(assignment.get("workOrder").get("id"), root.get("id")),
                criteriaBuilder.equal(assignment.get("assignee").get("id"), membershipId),
                criteriaBuilder.equal(assignment.get("state"), state),
                criteriaBuilder.equal(assignment.get("activeSlot"), slot)
        );
        return criteriaBuilder.exists(assignmentSubquery);
    }

    private static Predicate buildNoAssignmentPredicate(
            Root<WorkOrder> root,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            com.pro.ereportbacken.model.pojo.AssignmentState state,
            com.pro.ereportbacken.model.pojo.AssignmentSlot slot) {
        Subquery<UUID> assignmentSubquery = query.subquery(UUID.class);
        Root<com.pro.ereportbacken.model.pojo.WorkOrderAssignment> assignment =
                assignmentSubquery.from(com.pro.ereportbacken.model.pojo.WorkOrderAssignment.class);
        assignmentSubquery.select(assignment.get("id"));
        assignmentSubquery.where(
                criteriaBuilder.equal(assignment.get("tenantId"), root.get("tenantId")),
                criteriaBuilder.equal(assignment.get("workOrder").get("id"), root.get("id")),
                criteriaBuilder.equal(assignment.get("state"), state),
                criteriaBuilder.equal(assignment.get("activeSlot"), slot)
        );
        return criteriaBuilder.not(criteriaBuilder.exists(assignmentSubquery));
    }

    private static Predicate buildKeywordPredicate(Root<WorkOrder> root,
                                                   CriteriaBuilder criteriaBuilder,
                                                   String rawSearchTerm) {
        String normalizedSearchTerm = rawSearchTerm.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + escapeLike(normalizedSearchTerm) + "%";
        String rawPattern = "%" + escapeLike(rawSearchTerm.trim()) + "%";
        List<Predicate> keywordPredicates = new ArrayList<>();

        keywordPredicates.add(contains(root, criteriaBuilder, "location", pattern));
        keywordPredicates.add(contains(root, criteriaBuilder, "gpsCoordinates", pattern));
        keywordPredicates.add(containsLob(root, criteriaBuilder, "reason", rawPattern));
        keywordPredicates.add(contains(root, criteriaBuilder, "contactPerson", pattern));
        keywordPredicates.add(contains(root, criteriaBuilder, "phone", pattern));
        keywordPredicates.add(contains(root, criteriaBuilder, "status", pattern));
        keywordPredicates.add(containsLob(root, criteriaBuilder, "resolutionDetails", rawPattern));
        keywordPredicates.add(contains(root, criteriaBuilder, "resolutionMethod", pattern));
        keywordPredicates.add(containsLob(root, criteriaBuilder, "closureReason", rawPattern));
        keywordPredicates.add(containsLob(root, criteriaBuilder, "replacementResult", rawPattern));
        keywordPredicates.add(containsUser(root, criteriaBuilder, "reportedBy", pattern));
        keywordPredicates.add(containsUser(root, criteriaBuilder, "resolvedBy", pattern));
        keywordPredicates.add(containsUser(root, criteriaBuilder, "closedBy", pattern));

        parseWorkOrderId(normalizedSearchTerm).ifPresent(id ->
                keywordPredicates.add(criteriaBuilder.equal(root.get("id"), id)));

        List<WorkOrderType> matchedTypes = java.util.Arrays.stream(WorkOrderType.values())
                .filter(type -> type.name().toLowerCase(Locale.ROOT).contains(normalizedSearchTerm)
                        || type.getDisplayName().toLowerCase(Locale.ROOT).contains(normalizedSearchTerm))
                .toList();
        if (!matchedTypes.isEmpty()) {
            keywordPredicates.add(root.get("reportType").in(matchedTypes));
        }

        return criteriaBuilder.or(keywordPredicates.toArray(new Predicate[0]));
    }

    private static Predicate buildStatusPredicate(Root<WorkOrder> root,
                                                  CriteriaBuilder criteriaBuilder,
                                                  WorkOrderFilterDTO filterDTO) {
        List<String> requestedStatuses = normalizedFilterValues(filterDTO.getStatuses());
        if (requestedStatuses.isEmpty()) {
            return null;
        }
        List<Predicate> statusPredicates = new ArrayList<>();
        for (String requestedStatus : requestedStatuses) {
            try {
                com.pro.ereportbacken.model.pojo.WorkOrderStatus status =
                        com.pro.ereportbacken.model.pojo.WorkOrderStatus.fromValue(requestedStatus);
                statusPredicates.add(criteriaBuilder.equal(root.get("statusCode"), status.getCode()));
            } catch (IllegalArgumentException ignored) {
                statusPredicates.add(criteriaBuilder.equal(root.get("status"), requestedStatus));
            }
        }
        return criteriaBuilder.or(statusPredicates.toArray(new Predicate[0]));
    }

    private static Predicate buildReportTypePredicate(Root<WorkOrder> root,
                                                      CriteriaBuilder criteriaBuilder,
                                                      WorkOrderFilterDTO filterDTO) {
        List<String> requestedValues = normalizedFilterValues(filterDTO.getReportTypes());
        if (requestedValues.isEmpty()) {
            return null;
        }
        List<WorkOrderType> reportTypes = requestedValues.stream()
                .map(value -> WorkOrderType.valueOf(value.toUpperCase(Locale.ROOT)))
                .toList();
        return root.get("reportType").in(reportTypes);
    }

    private static Predicate buildQueuePredicate(Root<WorkOrder> root,
                                                 CriteriaQuery<?> query,
                                                 CriteriaBuilder criteriaBuilder,
                                                 WorkOrderFilterDTO filterDTO) {
        List<String> requestedQueues = normalizedFilterValues(filterDTO.getQueues());
        if (requestedQueues.isEmpty()) {
            return null;
        }
        List<Predicate> queuePredicates = requestedQueues.stream()
                .map(queue -> buildQueueOptionPredicate(root, query, criteriaBuilder, filterDTO, queue))
                .toList();
        return criteriaBuilder.or(queuePredicates.toArray(new Predicate[0]));
    }

    private static Predicate buildQueueOptionPredicate(Root<WorkOrder> root,
                                                       CriteriaQuery<?> query,
                                                       CriteriaBuilder criteriaBuilder,
                                                       WorkOrderFilterDTO filterDTO,
                                                       String requestedQueue) {
        String queue = requestedQueue.toUpperCase(Locale.ROOT);
        UUID membershipId = filterDTO.getAccessMembershipId();
        return switch (queue) {
            case "CLAIMABLE" -> buildClaimablePredicate(root, query, criteriaBuilder);
            case "ASSIGNED_TO_ME" -> membershipId == null ? criteriaBuilder.disjunction() :
                    buildAssignmentRelationshipPredicate(
                            root, query, criteriaBuilder, filterDTO.getTenantId(), membershipId,
                            com.pro.ereportbacken.model.pojo.AssignmentState.PENDING_ACCEPTANCE,
                            com.pro.ereportbacken.model.pojo.AssignmentSlot.PENDING);
            case "PROCESSING_BY_ME" -> membershipId == null ? criteriaBuilder.disjunction() : criteriaBuilder.and(
                    root.get("statusCode").in(
                            com.pro.ereportbacken.model.pojo.WorkOrderStatus.IN_PROGRESS.getCode(),
                            com.pro.ereportbacken.model.pojo.WorkOrderStatus.ON_HOLD.getCode()
                    ),
                    buildAssignmentRelationshipPredicate(
                            root, query, criteriaBuilder, filterDTO.getTenantId(), membershipId,
                            com.pro.ereportbacken.model.pojo.AssignmentState.ACCEPTED,
                            com.pro.ereportbacken.model.pojo.AssignmentSlot.CURRENT)
            );
            case "PENDING_VERIFICATION" -> criteriaBuilder.equal(
                    root.get("statusCode"),
                    com.pro.ereportbacken.model.pojo.WorkOrderStatus.PENDING_VERIFICATION.getCode()
            );
            case "REPORTED_BY_ME" -> membershipId == null ? criteriaBuilder.disjunction() : criteriaBuilder.equal(
                    root.get("reportedBy").get("id"), membershipId
            );
            default -> throw new IllegalArgumentException("工作队列无效：" + requestedQueue);
        };
    }

    private static List<String> normalizedFilterValues(java.util.Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        return normalized.stream().anyMatch(value -> "all".equalsIgnoreCase(value)) ? List.of() : normalized;
    }

    private static Predicate contains(Root<WorkOrder> root,
                                      CriteriaBuilder criteriaBuilder,
                                      String attributeName,
                                      String pattern) {
        Expression<String> value = criteriaBuilder.lower(
                criteriaBuilder.coalesce(root.<String>get(attributeName), criteriaBuilder.literal("")));
        return criteriaBuilder.like(value, pattern, '\\');
    }

    private static Predicate containsLob(Root<WorkOrder> root,
                                         CriteriaBuilder criteriaBuilder,
                                         String attributeName,
                                         String pattern) {
        // LONG32VARCHAR columns are represented by Hibernate as a non-string JDBC
        // expression.  Coalescing that expression directly with a String literal
        // makes Hibernate 7 reject LIKE during semantic validation.  The explicit
        // String cast keeps the predicate string-like while preserving the TEXT
        // column and its existing data.
        Expression<String> value = root.<String>get(attributeName).as(String.class);
        return criteriaBuilder.like(
                criteriaBuilder.coalesce(value, criteriaBuilder.literal("")), pattern, '\\');
    }

    private static Predicate containsUser(Root<WorkOrder> root,
                                          CriteriaBuilder criteriaBuilder,
                                          String associationName,
                                          String pattern) {
        Join<WorkOrder, User> userJoin = root.join(associationName, JoinType.LEFT);
        Predicate usernameMatches = criteriaBuilder.like(
                criteriaBuilder.lower(criteriaBuilder.coalesce(
                        userJoin.<String>get("username"), criteriaBuilder.literal(""))),
                pattern,
                '\\');
        Predicate nameMatches = criteriaBuilder.like(
                criteriaBuilder.lower(criteriaBuilder.coalesce(
                        userJoin.<String>get("name"), criteriaBuilder.literal(""))),
                pattern,
                '\\');
        return criteriaBuilder.or(usernameMatches, nameMatches);
    }

    private static java.util.Optional<UUID> parseWorkOrderId(String value) {
        Matcher matcher = WORK_ORDER_ID_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(UUID.fromString(matcher.group(1)));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    private static LocalDate parseReportDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + " 必须使用 yyyy-MM-dd 格式。", exception);
        }
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
