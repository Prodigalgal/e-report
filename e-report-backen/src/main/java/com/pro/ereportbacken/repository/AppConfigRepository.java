package com.pro.ereportbacken.repository;
import java.util.UUID;


import com.pro.ereportbacken.model.pojo.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, UUID>, JpaSpecificationExecutor<AppConfig> { // 主键类型是 String
    // 根据配置键查找配置项
    Optional<AppConfig> findByConfigKey(String configKey);
}

