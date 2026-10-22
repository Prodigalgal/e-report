package com.pro.ereportbacken.config;

import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.dto.OrganizationDirectorySnapshot;
import com.pro.ereportbacken.model.dto.WorkOrderStatisticsDTO;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.security.UserAuthorizationSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheSerializationTests {

    private final RedisSerializer<Object> serializer = CacheConfig.cacheValueSerializer();

    @Test
    void roundTripsAuthorizationSnapshotWithImmutableCollectionsAndTime() {
        UUID tenantId = uuid(1);
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                uuid(2),
                tenantId,
                uuid(3),
                7L,
                "worker",
                null,
                UserStatus.ACTIVE,
                List.of(new UserAuthorizationSnapshot.EffectivePermissionGrant(
                        uuid(4), "work-order:network-fault:view", false,
                        LocalDateTime.of(2026, 8, 9, 8, 0),
                        LocalDateTime.of(2026, 8, 9, 18, 0)
                ))
        );

        Object restored = serializer.deserialize(serializer.serialize(snapshot));

        assertThat(restored).isEqualTo(snapshot);
    }

    @Test
    void roundTripsOrganizationDirectoryRecord() {
        UUID tenantId = uuid(1);
        OrganizationDirectorySnapshot snapshot = new OrganizationDirectorySnapshot(List.of(
                new OrgUnitDTO(
                        uuid(2), tenantId, null, "NANJING", "南靖县",
                      OrgUnitType.COUNTY, "/" + uuid(2) + "/", TenantStatus.ACTIVE, false
                )
        ));

        Object restored = serializer.deserialize(serializer.serialize(snapshot));

        assertThat(restored).isEqualTo(snapshot);
    }

    @Test
    void roundTripsDashboardStatisticsWithNestedRecords() {
        WorkOrderStatisticsDTO statistics = new WorkOrderStatisticsDTO(
                3, 1, 1, 1, 0, 66.67, 0, 0, 0,
                1, 1, 0, 0, 0, 0, 1, 3600,
                List.of(new WorkOrderStatisticsDTO.TypeCountDTO(
                        WorkOrderType.NETWORK_FAULT, "网络故障", 3, 1, 1, 1, 0, 0, 0
                )),
                List.of(new WorkOrderStatisticsDTO.StatusCountDTO("处理中", 1)),
                List.of(new WorkOrderStatisticsDTO.DailyCountDTO(java.time.LocalDate.of(2026, 8, 9), 3)),
                List.of(new WorkOrderStatisticsDTO.CountyComparisonDTO(
                        uuid(2), "南靖县", 3, 1, 1, 66.67, 0, 0
                ))
        );

        Object restored = serializer.deserialize(serializer.serialize(statistics));

        assertThat(restored).isEqualTo(statistics);
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
