package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.OrgUnitMoveRequestDTO;
import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.OrganizationMoveEventRepository;
import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrgUnitMoveIntegrationTests {

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private OrgUnitRepository orgUnitRepository;

    @Autowired
    private OrganizationMoveEventRepository moveEventRepository;

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private TenantContext tenantContext;

    @Test
    void movesWholeSubtreeAndPersistsAuditEvent() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit root = defaultTenantService.requireRootOrgUnit(tenantId);
        OrgUnit source = organization(tenantId, root, "MOVE_SOURCE");
        OrgUnit child = organization(tenantId, source, "MOVE_CHILD");
        OrgUnit target = organization(tenantId, root, "MOVE_TARGET");

        var movedReference = new AtomicReference<OrgUnitDTO>();
        tenantContext.runInTenant(tenantId, () -> movedReference.set(orgUnitService.move(
                source.getId(), new OrgUnitMoveRequestDTO(target.getId(), "组织职责调整"))));
        var moved = movedReference.get();

        OrgUnit reloadedChild = orgUnitRepository.findByTenantIdAndId(tenantId, child.getId()).orElseThrow();
        assertThat(moved.parentId()).isEqualTo(target.getId());
        assertThat(moved.path()).isEqualTo(target.getPath() + source.getId() + "/");
        assertThat(reloadedChild.getPath()).isEqualTo(moved.path() + child.getId() + "/");
        assertThat(moveEventRepository.findByTenantIdAndOrgUnitIdOrderByCreatedAtDesc(
                tenantId, source.getId())).first().satisfies(event -> {
                    assertThat(event.getOldParentId()).isEqualTo(root.getId());
                    assertThat(event.getNewParentId()).isEqualTo(target.getId());
                    assertThat(event.getReason()).isEqualTo("组织职责调整");
                });

        assertThatThrownBy(() -> tenantContext.runInTenant(tenantId, () -> orgUnitService.move(
                target.getId(), new OrgUnitMoveRequestDTO(child.getId(), "制造组织树环"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("下级组织");
    }

    private OrgUnit organization(UUID tenantId, OrgUnit parent, String prefix) {
        OrgUnit organization = new OrgUnit();
        organization.setTenantId(tenantId);
        organization.setParentId(parent.getId());
        organization.setCode(prefix + System.nanoTime());
        organization.setName(prefix + "组织");
        organization.setUnitType(OrgUnitType.COUNTY);
        organization.setStatus(TenantStatus.ACTIVE);
        organization.setPath(parent.getPath() + "pending/");
        organization = orgUnitRepository.saveAndFlush(organization);
        organization.setPath(parent.getPath() + organization.getId() + "/");
        return orgUnitRepository.saveAndFlush(organization);
    }
}
