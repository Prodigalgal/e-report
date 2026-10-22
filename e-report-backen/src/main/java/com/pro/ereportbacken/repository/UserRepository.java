package com.pro.ereportbacken.repository;
import java.util.UUID;

import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import jakarta.persistence.LockModeType;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findBySystemKey(UserSystemKey systemKey);

    Optional<User> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByPhoneIn(Set<String> phones);

    List<User> findByEmailIn(Set<String> emails);

    List<User> findByUsernameIn(Set<String> usernames);

    Optional<User> findByTenantIdAndId(UUID tenantId, UUID id);

    List<User> findByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.id = :id")
    Optional<User> findByTenantIdAndIdForAuthorizationUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id);

    Optional<User> findByTenantIdAndUsernameAndStatus(UUID tenantId, String username, com.pro.ereportbacken.model.pojo.UserStatus status);

    List<User> findByTenantIdAndStatus(UUID tenantId, com.pro.ereportbacken.model.pojo.UserStatus status);

    @Query("""
            SELECT u.id FROM User u
            WHERE u.tenantId = :tenantId
              AND u.status = :status
              AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ESCAPE '!'
                   OR LOWER(COALESCE(u.name, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ESCAPE '!'
                   OR (:phoneticSearchTerm <> '' AND LOWER(COALESCE(u.namePinyin, '')) LIKE CONCAT(:phoneticSearchTerm, '%'))
                   OR (:phoneticSearchTerm <> '' AND LOWER(COALESCE(u.namePinyinInitials, '')) LIKE CONCAT(:phoneticSearchTerm, '%')))
            ORDER BY CASE
                WHEN LOWER(u.username) = LOWER(:searchTerm) THEN 0
                WHEN LOWER(COALESCE(u.name, '')) = LOWER(:searchTerm) THEN 1
                WHEN LOWER(COALESCE(u.namePinyin, '')) = :phoneticSearchTerm THEN 2
                WHEN LOWER(COALESCE(u.namePinyinInitials, '')) = :phoneticSearchTerm THEN 3
                ELSE 4
            END, u.name ASC, u.username ASC
            """)
    List<UUID> searchActiveUserIds(@Param("tenantId") UUID tenantId,
                                   @Param("status") com.pro.ereportbacken.model.pojo.UserStatus status,
                                   @Param("searchTerm") String searchTerm,
                                   @Param("phoneticSearchTerm") String phoneticSearchTerm,
                                   Pageable pageable);

    @Query("""
            SELECT u.id FROM User u
            WHERE u.tenantId = :tenantId
              AND u.status = :status
              AND u.orgUnitId IN :orgUnitIds
              AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ESCAPE '!'
                   OR LOWER(COALESCE(u.name, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ESCAPE '!'
                   OR (:phoneticSearchTerm <> '' AND LOWER(COALESCE(u.namePinyin, '')) LIKE CONCAT(:phoneticSearchTerm, '%'))
                   OR (:phoneticSearchTerm <> '' AND LOWER(COALESCE(u.namePinyinInitials, '')) LIKE CONCAT(:phoneticSearchTerm, '%')))
            ORDER BY CASE
                WHEN LOWER(u.username) = LOWER(:searchTerm) THEN 0
                WHEN LOWER(COALESCE(u.name, '')) = LOWER(:searchTerm) THEN 1
                WHEN LOWER(COALESCE(u.namePinyin, '')) = :phoneticSearchTerm THEN 2
                WHEN LOWER(COALESCE(u.namePinyinInitials, '')) = :phoneticSearchTerm THEN 3
                ELSE 4
            END, u.name ASC, u.username ASC
            """)
    List<UUID> searchActiveUserIdsInOrganizations(@Param("tenantId") UUID tenantId,
                                                  @Param("status") com.pro.ereportbacken.model.pojo.UserStatus status,
                                                  @Param("orgUnitIds") Set<UUID> orgUnitIds,
                                                  @Param("searchTerm") String searchTerm,
                                                  @Param("phoneticSearchTerm") String phoneticSearchTerm,
                                                  Pageable pageable);

    @Query("""
            SELECT u.id FROM User u
            WHERE u.namePinyin IS NULL OR u.namePinyinInitials IS NULL
            ORDER BY u.id
            """)
    List<UUID> findIdsMissingNameSearchKeys(Pageable pageable);

    @Query("""
            SELECT COUNT(u) FROM User u
            WHERE u.namePinyin IS NULL OR u.namePinyinInitials IS NULL
            """)
    long countMissingNameSearchKeys();

    List<User> findByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

    @Query("""
            SELECT u.id AS membershipId, u.orgUnitId AS orgUnitId
            FROM User u
            WHERE u.tenantId = :tenantId
              AND u.id IN :membershipIds
            """)
    List<MembershipOrganization> findMembershipOrganizationsByTenantIdAndMembershipIds(
            @Param("tenantId") UUID tenantId,
            @Param("membershipIds") Set<UUID> membershipIds);

    interface MembershipOrganization {
        UUID getMembershipId();

        UUID getOrgUnitId();
    }

    long countByTenantIdAndDepartmentId(UUID tenantId, UUID departmentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.departmentId = :targetDepartmentId,
                u.authorizationVersion = u.authorizationVersion + 1
            WHERE u.tenantId = :tenantId
              AND u.departmentId = :sourceDepartmentId
            """)
    int moveDepartmentMembers(@Param("tenantId") UUID tenantId,
                              @Param("sourceDepartmentId") UUID sourceDepartmentId,
                              @Param("targetDepartmentId") UUID targetDepartmentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.password = :upgradedPassword
            WHERE u.tenantId = :tenantId
              AND u.id = :membershipId
              AND u.password = :authenticatedPassword
            """)
    int upgradePasswordIfCurrent(@Param("tenantId") UUID tenantId,
                                 @Param("membershipId") UUID membershipId,
                                 @Param("authenticatedPassword") String authenticatedPassword,
                                 @Param("upgradedPassword") String upgradedPassword);

    @Query("""
            SELECT DISTINCT u.id AS userId, u.phone AS phone, u.email AS email,
                   u.emailWorkOrderCreated AS emailWorkOrderCreated,
                   u.emailWorkOrderSlaBreached AS emailWorkOrderSlaBreached,
                   u.emailWorkOrderAssigned AS emailWorkOrderAssigned,
                   u.emailDailyOutstandingReport AS emailDailyOutstandingReport
            FROM User u
            WHERE u.tenantId = :tenantId
              AND u.status = :status
              AND u.id IN :membershipIds
              AND u.orgUnitId IN :orgUnitIds
            ORDER BY u.id
            """)
    List<UserNotificationContact> findNotificationContactsByMembershipsAndOrganizations(
            @Param("tenantId") UUID tenantId,
            @Param("status") com.pro.ereportbacken.model.pojo.UserStatus status,
            @Param("membershipIds") Set<UUID> membershipIds,
            @Param("orgUnitIds") Set<UUID> orgUnitIds);

    @Query("""
            SELECT DISTINCT u.id AS userId, u.phone AS phone, u.email AS email,
                   u.emailWorkOrderCreated AS emailWorkOrderCreated,
                   u.emailWorkOrderSlaBreached AS emailWorkOrderSlaBreached,
                   u.emailWorkOrderAssigned AS emailWorkOrderAssigned,
                   u.emailDailyOutstandingReport AS emailDailyOutstandingReport
            FROM User u
            WHERE u.tenantId = :tenantId
              AND u.status = :status
              AND u.id IN :membershipIds
            ORDER BY u.id
            """)
    List<UserNotificationContact> findNotificationContactsByMemberships(
            @Param("tenantId") UUID tenantId,
            @Param("status") com.pro.ereportbacken.model.pojo.UserStatus status,
            @Param("membershipIds") Set<UUID> membershipIds);

    @Query("""
            SELECT new com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO(
                u.emailWorkOrderCreated, u.emailWorkOrderSlaBreached,
                u.emailWorkOrderAssigned, u.emailDailyOutstandingReport)
            FROM User u WHERE u.tenantId = :tenantId AND u.id = :membershipId
            """)
    Optional<com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO> findEmailNotificationPreferences(
            @Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE User u SET u.emailWorkOrderCreated = :created,
                u.emailWorkOrderSlaBreached = :slaBreached,
                u.emailWorkOrderAssigned = :assigned,
                u.emailDailyOutstandingReport = :dailyReport, u.updatedAt = :updatedAt
            WHERE u.tenantId = :tenantId AND u.id = :membershipId
            """)
    int updateEmailNotificationPreferences(
            @Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
            @Param("created") boolean created, @Param("slaBreached") boolean slaBreached,
            @Param("assigned") boolean assigned, @Param("dailyReport") boolean dailyReport,
            @Param("updatedAt") java.time.LocalDateTime updatedAt);

}
