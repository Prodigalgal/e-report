package com.pro.ereportbacken.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        PasswordEncoder argon2id = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
        PasswordEncoder bcryptEncoder = new BCryptPasswordEncoder();
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("argon2id", argon2id);
        encoders.put("bcrypt", bcryptEncoder);

        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("argon2id", encoders);
        delegating.setDefaultPasswordEncoderForMatches(bcryptEncoder);
        return delegating;
    }
}
