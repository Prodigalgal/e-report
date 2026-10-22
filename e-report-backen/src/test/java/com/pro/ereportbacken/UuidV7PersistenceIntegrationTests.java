package com.pro.ereportbacken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UuidV7PersistenceIntegrationTests {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatesVersionSevenUuidAndSerializesItAsStandardJsonString() throws Exception {
        Permission permission = new Permission();
        permission.setName("test:uuid-v7:" + System.nanoTime());
        permission.setDisplayName("UUIDv7测试权限");
        permission = permissionRepository.saveAndFlush(permission);

        assertThat(permission.getId()).isNotNull();
        assertThat(permission.getId().version()).isEqualTo(7);
        assertThat(objectMapper.writeValueAsString(Map.of("id", permission.getId())))
                .isEqualTo("{\"id\":\"" + permission.getId() + "\"}");
    }
}
