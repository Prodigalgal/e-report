package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO;
import com.pro.ereportbacken.notification.EmailNotificationPreferencesService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EmailNotificationPreferencesRepositoryTests {
    private final UserRepository users;
    private final EmailNotificationPreferencesService preferences;
    private final EntityManager entityManager;

    EmailNotificationPreferencesRepositoryTests(UserRepository users, EmailNotificationPreferencesService preferences,
                                               EntityManager entityManager) {
        this.users = users;
        this.preferences = preferences;
        this.entityManager = entityManager;
    }

    @Test
    void defaultsAreEnabledAndSaveChangesOnlyTheFourSubscriptionFields() {
        var user = users.findAll().getFirst();
        String name = user.getName();
        String password = user.getPassword();
        assertThat(preferences.get(user.getTenantId(), user.getId()))
                .isEqualTo(new EmailNotificationPreferencesDTO(true, true, true, true));

        var selected = new EmailNotificationPreferencesDTO(false, true, false, true);
        preferences.update(user.getTenantId(), user.getId(), selected);

        assertThat(preferences.get(user.getTenantId(), user.getId())).isEqualTo(selected);
        var reloaded = users.findByTenantIdAndId(user.getTenantId(), user.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo(name);
        assertThat(reloaded.getPassword()).isEqualTo(password);
        var contact = users.findNotificationContactsByMemberships(user.getTenantId(), user.getStatus(), Set.of(user.getId())).getFirst();
        assertThat(contact.getEmailWorkOrderCreated()).isFalse();
        assertThat(contact.getEmailWorkOrderSlaBreached()).isTrue();
        assertThat(contact.getEmailWorkOrderAssigned()).isFalse();
        assertThat(contact.getEmailDailyOutstandingReport()).isTrue();
    }

    @Test
    void tenantBoundaryAlsoAppliesToTheDatabaseUpdate() {
        var user = users.findAll().getFirst();
        assertThatThrownBy(() -> preferences.update(UUID.randomUUID(), user.getId(),
                new EmailNotificationPreferencesDTO(false, false, false, false)))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThat(users.findEmailNotificationPreferences(user.getTenantId(), user.getId()))
                .contains(new EmailNotificationPreferencesDTO(true, true, true, true));
    }

    @Test
    void savingAProfileDoesNotRestoreSubscriptionsChangedAfterItWasLoaded() {
        var user = users.findAll().getFirst();
        entityManager.createQuery("UPDATE User u SET u.emailWorkOrderCreated = false WHERE u.id = :id")
                .setParameter("id", user.getId()).executeUpdate();

        user.setName("订阅并发回归");
        users.saveAndFlush(user);
        entityManager.clear();

        var reloaded = users.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("订阅并发回归");
        assertThat(reloaded.isEmailWorkOrderCreated()).isFalse();
    }
}
