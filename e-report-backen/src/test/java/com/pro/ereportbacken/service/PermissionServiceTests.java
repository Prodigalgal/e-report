package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTests {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private OrganizationScopeAuthorizationService scopeAuthorizationService;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(permissionRepository, scopeAuthorizationService);
    }

    @Test
    void listQueriesOnlyCurrentFixedCatalogEvenWhenDatabaseMayContainRetiredRows() {
        when(scopeAuthorizationService.hasAuthority(any())).thenReturn(true);
        when(permissionRepository.findByNameIn(any())).thenAnswer(invocation -> {
            Collection<String> requestedNames = invocation.getArgument(0);
            return requestedNames.stream().map(name -> {
                Permission permission = new Permission(name, null, null);
                permission.setId(UUID.randomUUID());
                return permission;
            }).toList();
        });

        var page = permissionService.findAllPermissions(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(20)
                .allSatisfy(permission -> assertThat(PermissionCatalog.contains(permission.getName())).isTrue());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> names = ArgumentCaptor.forClass(Collection.class);
        verify(permissionRepository).findByNameIn(names.capture());
        assertThat(names.getValue()).allMatch(PermissionCatalog::contains)
                .doesNotContain("retired:permission");
    }

    @Test
    void retiredPermissionCannotBeReadByIdentifier() {
        Permission retired = new Permission("retired:permission", "已退役权限", "不应展示");
        retired.setId(UUID.randomUUID());
        when(permissionRepository.findById(retired.getId())).thenReturn(java.util.Optional.of(retired));

        assertThatThrownBy(() -> permissionService.findPermissionById(retired.getId()))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
