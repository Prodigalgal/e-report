package com.pro.ereportbacken;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EReportBackenApplication {

    @PostConstruct
    void requireUtcJvmTimeZone() {
        var rules = ZoneId.systemDefault().getRules();
        if (!rules.isFixedOffset() || !ZoneOffset.UTC.equals(rules.getOffset(Instant.now()))) {
            throw new IllegalStateException("Backend JVM 必须以 UTC 时区启动（-Duser.timezone=UTC）。");
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(EReportBackenApplication.class, args);
    }

}
