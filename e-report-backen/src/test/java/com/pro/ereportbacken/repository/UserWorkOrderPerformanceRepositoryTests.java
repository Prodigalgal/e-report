package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserWorkOrderPerformanceRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWorkOrderPerformanceRepository performanceRepository;

    @Test
    void executesPortableJpaPerformanceQueriesForUserWithoutWorkOrders() {
        User user = userRepository.findAll().stream().findFirst().orElseThrow();

        UserWorkOrderPerformanceRepository.UserWorkOrderPerformanceProjection summary =
                performanceRepository.summarize(user.getTenantId(), user.getId(), LocalDateTime.now().minusDays(30));

        assertThat(summary.getReportedTotal()).isGreaterThanOrEqualTo(0);
        assertThat(summary.getAssignedTotal()).isGreaterThanOrEqualTo(0);
        assertThat(summary.getResponsesSubmitted()).isGreaterThanOrEqualTo(0);
        assertThat(summary.getVerificationsCompleted()).isGreaterThanOrEqualTo(0);
    }
}
