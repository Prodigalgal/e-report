package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface UserWorkOrderPerformanceRepository extends Repository<User, UUID> {

    @Query("""
            SELECT COUNT(report) AS reportedTotal,
                   COALESCE(SUM(CASE WHEN report.submittedAt >= :since THEN 1 ELSE 0 END), 0) AS reportedLast30Days,
                   COALESCE(SUM(CASE WHEN report.statusCode NOT IN ('CLOSED', 'CANCELLED') THEN 1 ELSE 0 END), 0) AS reportedOpen,
                   COALESCE(SUM(CASE WHEN report.statusCode = 'CLOSED' THEN 1 ELSE 0 END), 0) AS reportedClosed,
                   COALESCE(AVG(CASE WHEN report.statusCode = 'CLOSED' AND report.closedAt IS NOT NULL
                       THEN timestampdiff(second, report.submittedAt, report.closedAt) END) / 3600.0, 0) AS averageReportedClosureHours
            FROM WorkOrder report
            WHERE report.tenantId = :tenantId AND report.reportedBy.id = :userId
            """)
    ReportPerformanceProjection summarizeReports(@Param("tenantId") UUID tenantId,
                                                  @Param("userId") UUID userId,
                                                  @Param("since") LocalDateTime since);

    @Query("""
            SELECT COUNT(DISTINCT assignment.workOrder.id) AS assignedTotal,
                   COALESCE(SUM(CASE WHEN assignment.state IN (
                       com.pro.ereportbacken.model.pojo.AssignmentState.PENDING_ACCEPTANCE,
                       com.pro.ereportbacken.model.pojo.AssignmentState.ACCEPTED
                   ) THEN 1 ELSE 0 END), 0) AS activeAssignments
            FROM WorkOrderAssignment assignment
            WHERE assignment.tenantId = :tenantId AND assignment.assignee.id = :userId
            """)
    AssignmentPerformanceProjection summarizeAssignments(@Param("tenantId") UUID tenantId,
                                                          @Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(responseRow) AS responsesSubmitted,
                   COALESCE(SUM(CASE WHEN responseRow.submittedAt >= :since THEN 1 ELSE 0 END), 0) AS responsesLast30Days,
                   COALESCE(SUM(CASE WHEN timestampdiff(second,
                       COALESCE(responseRow.assignment.acceptedAt, responseRow.assignment.assignedAt),
                       responseRow.submittedAt) <= 86400 THEN 1 ELSE 0 END), 0) AS responsesWithin24Hours,
                   COALESCE(AVG(timestampdiff(second,
                       COALESCE(responseRow.assignment.acceptedAt, responseRow.assignment.assignedAt),
                       responseRow.submittedAt)) / 60.0, 0) AS averageResponseMinutes
            FROM WorkOrderResponse responseRow
            WHERE responseRow.tenantId = :tenantId AND responseRow.submittedBy.id = :userId
            """)
    ResponsePerformanceProjection summarizeResponses(@Param("tenantId") UUID tenantId,
                                                      @Param("userId") UUID userId,
                                                      @Param("since") LocalDateTime since);

    @Query("""
            SELECT COUNT(verification) AS verificationsCompleted,
                   COALESCE(SUM(CASE WHEN verification.decision =
                       com.pro.ereportbacken.model.pojo.VerificationDecision.APPROVED THEN 1 ELSE 0 END), 0)
                       AS verificationsApproved,
                   COALESCE(AVG(timestampdiff(second,
                       verification.response.submittedAt, verification.verifiedAt)) / 60.0, 0)
                       AS averageVerificationMinutes
            FROM WorkOrderVerification verification
            WHERE verification.tenantId = :tenantId AND verification.verifiedBy.id = :userId
            """)
    VerificationPerformanceProjection summarizeVerifications(@Param("tenantId") UUID tenantId,
                                                              @Param("userId") UUID userId);

    default UserWorkOrderPerformanceProjection summarize(UUID tenantId, UUID userId, LocalDateTime since) {
        ReportPerformanceProjection reports = summarizeReports(tenantId, userId, since);
        AssignmentPerformanceProjection assignments = summarizeAssignments(tenantId, userId);
        ResponsePerformanceProjection responses = summarizeResponses(tenantId, userId, since);
        VerificationPerformanceProjection verifications = summarizeVerifications(tenantId, userId);
        return new UserWorkOrderPerformanceSummary(
                reports.getReportedTotal(),
                reports.getReportedLast30Days(),
                reports.getReportedOpen(),
                reports.getReportedClosed(),
                assignments.getAssignedTotal(),
                assignments.getActiveAssignments(),
                responses.getResponsesSubmitted(),
                responses.getResponsesLast30Days(),
                responses.getResponsesWithin24Hours(),
                verifications.getVerificationsCompleted(),
                verifications.getVerificationsApproved(),
                responses.getAverageResponseMinutes(),
                verifications.getAverageVerificationMinutes(),
                reports.getAverageReportedClosureHours());
    }

    interface ReportPerformanceProjection {
        long getReportedTotal();

        long getReportedLast30Days();

        long getReportedOpen();

        long getReportedClosed();

        double getAverageReportedClosureHours();
    }

    interface AssignmentPerformanceProjection {
        long getAssignedTotal();

        long getActiveAssignments();
    }

    interface ResponsePerformanceProjection {
        long getResponsesSubmitted();

        long getResponsesLast30Days();

        long getResponsesWithin24Hours();

        double getAverageResponseMinutes();
    }

    interface VerificationPerformanceProjection {
        long getVerificationsCompleted();

        long getVerificationsApproved();

        double getAverageVerificationMinutes();
    }

    interface UserWorkOrderPerformanceProjection {
        long getReportedTotal();

        long getReportedLast30Days();

        long getReportedOpen();

        long getReportedClosed();

        long getAssignedTotal();

        long getActiveAssignments();

        long getResponsesSubmitted();

        long getResponsesLast30Days();

        long getResponsesWithin24Hours();

        long getVerificationsCompleted();

        long getVerificationsApproved();

        double getAverageResponseMinutes();

        double getAverageVerificationMinutes();

        double getAverageReportedClosureHours();
    }

    record UserWorkOrderPerformanceSummary(
            long reportedTotal,
            long reportedLast30Days,
            long reportedOpen,
            long reportedClosed,
            long assignedTotal,
            long activeAssignments,
            long responsesSubmitted,
            long responsesLast30Days,
            long responsesWithin24Hours,
            long verificationsCompleted,
            long verificationsApproved,
            double averageResponseMinutes,
            double averageVerificationMinutes,
            double averageReportedClosureHours) implements UserWorkOrderPerformanceProjection {

        @Override
        public long getReportedTotal() {
            return reportedTotal;
        }

        @Override
        public long getReportedLast30Days() {
            return reportedLast30Days;
        }

        @Override
        public long getReportedOpen() {
            return reportedOpen;
        }

        @Override
        public long getReportedClosed() {
            return reportedClosed;
        }

        @Override
        public long getAssignedTotal() {
            return assignedTotal;
        }

        @Override
        public long getActiveAssignments() {
            return activeAssignments;
        }

        @Override
        public long getResponsesSubmitted() {
            return responsesSubmitted;
        }

        @Override
        public long getResponsesLast30Days() {
            return responsesLast30Days;
        }

        @Override
        public long getResponsesWithin24Hours() {
            return responsesWithin24Hours;
        }

        @Override
        public long getVerificationsCompleted() {
            return verificationsCompleted;
        }

        @Override
        public long getVerificationsApproved() {
            return verificationsApproved;
        }

        @Override
        public double getAverageResponseMinutes() {
            return averageResponseMinutes;
        }

        @Override
        public double getAverageVerificationMinutes() {
            return averageVerificationMinutes;
        }

        @Override
        public double getAverageReportedClosureHours() {
            return averageReportedClosureHours;
        }
    }
}
