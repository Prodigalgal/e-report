package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserNameSearchKeyBackfillService {

    private final UserRepository userRepository;

    public UserNameSearchKeyBackfillService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public int backfillNextBatch(int batchSize) {
        List<UUID> userIds = userRepository.findIdsMissingNameSearchKeys(PageRequest.of(0, batchSize));
        if (userIds.isEmpty()) {
            return 0;
        }
        List<User> users = userRepository.findAllById(userIds);
        users.forEach(User::refreshNameSearchKeys);
        userRepository.saveAll(users);
        userRepository.flush();
        return users.size();
    }

    @Transactional(readOnly = true)
    public long remainingCount() {
        return userRepository.countMissingNameSearchKeys();
    }
}
