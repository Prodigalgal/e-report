package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.time.UtcTime;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EmailNotificationPreferencesService {
    private final UserRepository userRepository;

    public EmailNotificationPreferencesService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public EmailNotificationPreferencesDTO get(UUID tenantId, UUID membershipId) {
        return userRepository.findEmailNotificationPreferences(tenantId, membershipId)
                .orElseThrow(() -> new EntityNotFoundException("当前账号不存在。"));
    }

    @Transactional
    public EmailNotificationPreferencesDTO update(UUID tenantId, UUID membershipId,
                                                   EmailNotificationPreferencesDTO preferences) {
        if (preferences == null || preferences.workOrderCreated() == null
                || preferences.workOrderSlaBreached() == null || preferences.workOrderAssigned() == null
                || preferences.dailyOutstandingReport() == null) {
            throw new IllegalArgumentException("请提供完整的四项邮件订阅设置。");
        }
        int updated = userRepository.updateEmailNotificationPreferences(
                tenantId, membershipId, preferences.workOrderCreated(), preferences.workOrderSlaBreached(),
                preferences.workOrderAssigned(), preferences.dailyOutstandingReport(), UtcTime.now());
        if (updated != 1) throw new EntityNotFoundException("当前账号不存在。");
        return preferences;
    }
}
