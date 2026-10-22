package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.time.UtcTime;
import java.util.UUID;

import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.specification.WorkOrderSpecification;
import com.pro.ereportbacken.time.DisplayTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;

@Repository
public class WorkOrderQueryRepository {
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "id",
            "location",
            "status",
            "contactPerson",
            "phone",
            "reportType",
            "reportTime",
            "submittedAt",
            "lastUpdatedTime",
            "resolutionTime",
            "slaDueAt",
            "statusCode",
            "priority",
            "workOrderNo",
            "closedAt"
    );

    private final EntityManager entityManager;

    public WorkOrderQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<UUID> findIdsByCriteria(WorkOrderFilterDTO filterDTO, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        List<UUID> ids = findPageIds(filterDTO, pageable, criteriaBuilder);
        long total = countByCriteria(filterDTO, criteriaBuilder);
        return new PageImpl<>(ids, pageable, total);
    }

    public List<UUID> findPageIdsByCriteria(WorkOrderFilterDTO filterDTO, Pageable pageable) {
        return findPageIds(filterDTO, pageable, entityManager.getCriteriaBuilder());
    }

    public ScopedStatistics summarizeByCriteria(WorkOrderFilterDTO filterDTO,
                                                LocalDate displayStartDate,
                                                int dayCount) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        return new ScopedStatistics(
                summarizeTypes(filterDTO, criteriaBuilder),
                summarizeStatuses(filterDTO, criteriaBuilder),
                summarizeDaily(filterDTO, displayStartDate, dayCount, criteriaBuilder),
                summarizeSla(filterDTO, UtcTime.now(), criteriaBuilder)
        );
    }

    public List<ReportingOrganizationSummary> summarizeReportingOrganizations(WorkOrderFilterDTO filterDTO,
                                                                               LocalDateTime now) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = criteriaBuilder.createTupleQuery();
        Root<WorkOrder> root = query.from(WorkOrder.class);
        Predicate active = root.get("statusCode").in(
                WorkOrderStatus.CLOSED.getCode(),
                WorkOrderStatus.CANCELLED.getCode()
        ).not();
        query.multiselect(
                root.get("reportingOrgId").alias("reportingOrgId"),
                criteriaBuilder.count(root).alias("total"),
                sumWhen(criteriaBuilder, active).alias("active"),
                sumWhen(criteriaBuilder, criteriaBuilder.equal(
                        root.get("statusCode"), WorkOrderStatus.CLOSED.getCode())).alias("closed"),
                sumWhen(criteriaBuilder, criteriaBuilder.and(
                        active, criteriaBuilder.isNotNull(root.get("slaBreachedAt")))).alias("currentSlaBreached"),
                sumWhen(criteriaBuilder, criteriaBuilder.and(
                        active, criteriaBuilder.lessThan(root.get("submittedAt"), now.minusHours(72)))).alias("over72Hours")
        );
        applyPredicate(query, root, filterDTO, criteriaBuilder);
        query.groupBy(root.get("reportingOrgId"));
        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new ReportingOrganizationSummary(
                        row.get("reportingOrgId", UUID.class),
                        longValue(row.get("total")),
                        longValue(row.get("active")),
                        longValue(row.get("closed")),
                        longValue(row.get("currentSlaBreached")),
                        longValue(row.get("over72Hours"))))
                .toList();
    }

    private SlaSummary summarizeSla(WorkOrderFilterDTO filterDTO,
                                    LocalDateTime now,
                                    CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<Tuple> query = criteriaBuilder.createTupleQuery();
        Root<WorkOrder> root = query.from(WorkOrder.class);
        Predicate active = root.get("statusCode").in(
                WorkOrderStatus.CLOSED.getCode(),
                WorkOrderStatus.CANCELLED.getCode()
        ).not();
        LocalDateTime before24Hours = now.minusHours(24);
        LocalDateTime before48Hours = now.minusHours(48);
        LocalDateTime before72Hours = now.minusHours(72);
        query.multiselect(
                sumWhen(criteriaBuilder, active).alias("active"),
                sumWhen(criteriaBuilder, criteriaBuilder.and(active,
                        criteriaBuilder.greaterThanOrEqualTo(root.get("submittedAt"), before24Hours))).alias("within24Hours"),
                sumWhen(criteriaBuilder, criteriaBuilder.and(active,
                        criteriaBuilder.lessThan(root.get("submittedAt"), before24Hours),
                        criteriaBuilder.greaterThanOrEqualTo(root.get("submittedAt"), before48Hours))).alias("from24To48Hours"),
                sumWhen(criteriaBuilder, criteriaBuilder.and(active,
                        criteriaBuilder.lessThan(root.get("submittedAt"), before48Hours),
                        criteriaBuilder.greaterThanOrEqualTo(root.get("submittedAt"), before72Hours))).alias("from48To72Hours"),
                sumWhen(criteriaBuilder, criteriaBuilder.and(active,
                        criteriaBuilder.lessThan(root.get("submittedAt"), before72Hours))).alias("over72Hours"),
                sumWhen(criteriaBuilder, criteriaBuilder.and(active,
                        criteriaBuilder.isNotNull(root.get("slaBreachedAt")))).alias("currentSlaBreached"),
                sumWhen(criteriaBuilder, criteriaBuilder.isTrue(root.get("slaEverBreached"))).alias("everSlaBreached")
        );
        applyPredicate(query, root, filterDTO, criteriaBuilder);
        Tuple totals = entityManager.createQuery(query).getSingleResult();
        return new SlaSummary(
                longValue(totals.get("active")),
                longValue(totals.get("within24Hours")),
                longValue(totals.get("from24To48Hours")),
                longValue(totals.get("from48To72Hours")),
                longValue(totals.get("over72Hours")),
                longValue(totals.get("currentSlaBreached")),
                longValue(totals.get("everSlaBreached")),
                findOldestActiveSubmittedAt(filterDTO, activeStatusCodes(), criteriaBuilder)
        );
    }

    private LocalDateTime findOldestActiveSubmittedAt(WorkOrderFilterDTO filterDTO,
                                                       Set<String> activeStatusCodes,
                                                       CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<LocalDateTime> query = criteriaBuilder.createQuery(LocalDateTime.class);
        Root<WorkOrder> root = query.from(WorkOrder.class);
        query.select(criteriaBuilder.least(root.<LocalDateTime>get("submittedAt")));
        Predicate scope = predicate(root, query, filterDTO, criteriaBuilder);
        Predicate active = root.get("statusCode").in(activeStatusCodes);
        query.where(scope == null ? active : criteriaBuilder.and(scope, active));
        return entityManager.createQuery(query).getSingleResult();
    }

    private Set<String> activeStatusCodes() {
        return Set.of(
                WorkOrderStatus.SUBMITTED.getCode(),
                WorkOrderStatus.IN_PROGRESS.getCode(),
                WorkOrderStatus.PENDING_VERIFICATION.getCode(),
                WorkOrderStatus.ON_HOLD.getCode()
        );
    }

    private List<TypeSummary> summarizeTypes(WorkOrderFilterDTO filterDTO, CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<Tuple> query = criteriaBuilder.createTupleQuery();
        Root<WorkOrder> root = query.from(WorkOrder.class);
        Expression<Long> total = criteriaBuilder.count(root);
        Expression<Long> pending = sumWhen(criteriaBuilder, root.get("statusCode").in(
                WorkOrderStatus.SUBMITTED.getCode(),
                WorkOrderStatus.IN_PROGRESS.getCode(),
                WorkOrderStatus.ON_HOLD.getCode()
        ));
        Expression<Long> resolved = sumWhen(criteriaBuilder,
                criteriaBuilder.equal(root.get("statusCode"), WorkOrderStatus.PENDING_VERIFICATION.getCode()));
        Expression<Long> closed = sumWhen(criteriaBuilder,
                criteriaBuilder.equal(root.get("statusCode"), WorkOrderStatus.CLOSED.getCode()));
        Expression<Long> boxQuantity = criteriaBuilder.sum(
                criteriaBuilder.toLong(criteriaBuilder.coalesce(root.<Integer>get("boxQuantity"), 0)));
        Expression<Long> replacementRequired = sumWhen(criteriaBuilder, criteriaBuilder.isTrue(root.get("replacementRequired")));
        Expression<Long> replacementCompleted = sumWhen(criteriaBuilder, criteriaBuilder.isTrue(root.get("replacementCompleted")));

        query.multiselect(
                root.get("reportType").alias("reportType"),
                total.alias("total"),
                pending.alias("pending"),
                resolved.alias("resolved"),
                closed.alias("closed"),
                boxQuantity.alias("boxQuantity"),
                replacementRequired.alias("replacementRequired"),
                replacementCompleted.alias("replacementCompleted")
        );
        applyPredicate(query, root, filterDTO, criteriaBuilder);
        query.groupBy(root.get("reportType"));
        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new TypeSummary(
                        row.get("reportType", WorkOrderType.class),
                        longValue(row.get("total")),
                        longValue(row.get("pending")),
                        longValue(row.get("resolved")),
                        longValue(row.get("closed")),
                        longValue(row.get("boxQuantity")),
                        longValue(row.get("replacementRequired")),
                        longValue(row.get("replacementCompleted"))
                ))
                .toList();
    }

    private List<StatusSummary> summarizeStatuses(WorkOrderFilterDTO filterDTO, CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<Tuple> query = criteriaBuilder.createTupleQuery();
        Root<WorkOrder> root = query.from(WorkOrder.class);
        query.multiselect(root.get("statusCode").alias("statusCode"), criteriaBuilder.count(root).alias("total"));
        applyPredicate(query, root, filterDTO, criteriaBuilder);
        query.groupBy(root.get("statusCode"));
        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new StatusSummary(row.get("statusCode", String.class), longValue(row.get("total"))))
                .toList();
    }

    private Map<LocalDate, Long> summarizeDaily(WorkOrderFilterDTO filterDTO,
                                                LocalDate displayStartDate,
                                                int dayCount,
                                                CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<Tuple> query = criteriaBuilder.createTupleQuery();
        Root<WorkOrder> root = query.from(WorkOrder.class);
        List<Selection<?>> dailyCounts = new ArrayList<>(dayCount);
        for (int dayOffset = 0; dayOffset < dayCount; dayOffset++) {
            LocalDate displayDate = displayStartDate.plusDays(dayOffset);
            LocalDateTime startUtc = DisplayTime.startOfDisplayDateUtc(displayDate);
            LocalDateTime endUtc = DisplayTime.startOfDisplayDateUtc(displayDate.plusDays(1));
            dailyCounts.add(sumWhen(criteriaBuilder, criteriaBuilder.and(
                    criteriaBuilder.greaterThanOrEqualTo(root.get("submittedAt"), startUtc),
                    criteriaBuilder.lessThan(root.get("submittedAt"), endUtc)
            )).alias("day" + dayOffset));
        }
        query.multiselect(dailyCounts);
        Predicate accessPredicate = predicate(root, query, filterDTO, criteriaBuilder);
        Predicate datePredicate = criteriaBuilder.and(
                criteriaBuilder.greaterThanOrEqualTo(root.get("submittedAt"),
                        DisplayTime.startOfDisplayDateUtc(displayStartDate)),
                criteriaBuilder.lessThan(root.get("submittedAt"),
                        DisplayTime.startOfDisplayDateUtc(displayStartDate.plusDays(dayCount)))
        );
        query.where(accessPredicate == null ? datePredicate : criteriaBuilder.and(accessPredicate, datePredicate));
        Tuple totals = entityManager.createQuery(query).getSingleResult();
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        for (int dayOffset = 0; dayOffset < dayCount; dayOffset++) {
            result.put(displayStartDate.plusDays(dayOffset), longValue(totals.get("day" + dayOffset)));
        }
        return result;
    }

    private Expression<Long> sumWhen(CriteriaBuilder criteriaBuilder, Predicate predicate) {
        return criteriaBuilder.sum(criteriaBuilder.<Long>selectCase().when(predicate, 1L).otherwise(0L));
    }

    private void applyPredicate(CriteriaQuery<?> query,
                                Root<WorkOrder> root,
                                WorkOrderFilterDTO filterDTO,
                                CriteriaBuilder criteriaBuilder) {
        Predicate predicate = predicate(root, query, filterDTO, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    private Predicate predicate(Root<WorkOrder> root,
                                CriteriaQuery<?> query,
                                WorkOrderFilterDTO filterDTO,
                                CriteriaBuilder criteriaBuilder) {
        return WorkOrderSpecification.getWorkOrdersByCriteria(filterDTO)
                .toPredicate(root, query, criteriaBuilder);
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private List<UUID> findPageIds(WorkOrderFilterDTO filterDTO, Pageable pageable, CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<UUID> query = criteriaBuilder.createQuery(UUID.class);
        Root<WorkOrder> root = query.from(WorkOrder.class);
        query.select(root.get("id"));

        Predicate predicate = WorkOrderSpecification.getWorkOrdersByCriteria(filterDTO)
                .toPredicate(root, query, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        query.orderBy(resolveOrders(pageable.getSort(), root, criteriaBuilder));

        TypedQuery<UUID> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(Math.toIntExact(pageable.getOffset()));
        typedQuery.setMaxResults(pageable.getPageSize());
        return typedQuery.getResultList();
    }

    private long countByCriteria(WorkOrderFilterDTO filterDTO, CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
        Root<WorkOrder> root = query.from(WorkOrder.class);
        query.select(criteriaBuilder.count(root));

        Predicate predicate = WorkOrderSpecification.getWorkOrdersByCriteria(filterDTO)
                .toPredicate(root, query, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        return entityManager.createQuery(query).getSingleResult();
    }

    private List<Order> resolveOrders(Sort sort, Root<WorkOrder> root, CriteriaBuilder criteriaBuilder) {
        List<Order> orders = new ArrayList<>();
        boolean hasIdOrder = false;
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                continue;
            }
            if ("id".equals(order.getProperty())) {
                hasIdOrder = true;
            }
            orders.add(order.isAscending()
                    ? criteriaBuilder.asc(root.get(order.getProperty()))
                    : criteriaBuilder.desc(root.get(order.getProperty())));
        }
        if (orders.isEmpty()) {
            orders.add(criteriaBuilder.desc(root.get("reportTime")));
        }
        if (!hasIdOrder) {
            orders.add(criteriaBuilder.desc(root.get("id")));
        }
        return orders;
    }

    public record ScopedStatistics(
            List<TypeSummary> byType,
            List<StatusSummary> byStatus,
            Map<LocalDate, Long> daily,
            SlaSummary sla
    ) {
    }

    public record TypeSummary(
            WorkOrderType reportType,
            long total,
            long pending,
            long resolved,
            long closed,
            long boxQuantityTotal,
            long replacementRequired,
            long replacementCompleted
    ) {
    }

    public record StatusSummary(String statusCode, long total) {
    }

    public record SlaSummary(
            long active,
            long within24Hours,
            long from24To48Hours,
            long from48To72Hours,
            long over72Hours,
            long currentSlaBreached,
            long everSlaBreached,
            LocalDateTime oldestActiveSubmittedAt
    ) {
    }

    public record ReportingOrganizationSummary(
            UUID reportingOrgUnitId,
            long total,
            long active,
            long closed,
            long currentSlaBreached,
            long over72Hours
    ) {
    }
}


