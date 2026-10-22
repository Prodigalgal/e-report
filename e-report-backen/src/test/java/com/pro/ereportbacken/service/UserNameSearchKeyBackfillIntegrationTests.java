package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserNameSearchKeyBackfillIntegrationTests {

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserNameSearchKeyBackfillService backfillService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void backfillsMissingKeysInBoundedBatchAndCanRunAgain() {
        var tenantId = defaultTenantService.requireDefaultTenant().getId();
        User target = userRepository.findByTenantIdAndUsername(tenantId, "nanjing").orElseThrow();
        target.setName("林智博");
        userRepository.saveAndFlush(target);

        jdbcTemplate.update(
                "UPDATE users SET name_pinyin = NULL, name_pinyin_initials = NULL WHERE id = ?",
                target.getId());
        entityManager.clear();

        assertThat(backfillService.remainingCount()).isPositive();
        assertThat(backfillService.backfillNextBatch(200)).isPositive();
        entityManager.clear();

        User backfilled = userRepository.findById(target.getId()).orElseThrow();
        assertThat(backfilled.getNamePinyin()).isEqualTo("linzhibo");
        assertThat(backfilled.getNamePinyinInitials()).isEqualTo("lzb");
        assertThat(backfillService.backfillNextBatch(200)).isZero();
    }
}
