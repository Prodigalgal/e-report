package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.ClientObservationEvent;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ClientObservationEventRepository extends JpaRepository<ClientObservationEvent, UUID>,
        JpaSpecificationExecutor<ClientObservationEvent> {

    List<ClientObservationEvent> findByClientEventIdIn(Collection<String> clientEventIds);

    List<ClientObservationEvent> findTop500ByTenantIdAndReceivedAtBeforeOrderByReceivedAtAsc(
            UUID tenantId,
            LocalDateTime cutoff
    );

    List<ClientObservationEvent> findTop500ByTenantIdAndEventTypeAndSeverityAndReceivedAtBeforeOrderByReceivedAtAsc(
            UUID tenantId,
            ObservationEventType eventType,
            ObservationSeverity severity,
            LocalDateTime cutoff
    );
}
