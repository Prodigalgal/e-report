package com.pro.ereportbacken.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.AuthorizationPlanRequestDTO;
import com.pro.ereportbacken.model.dto.UserCreateRequestDTO;
import com.pro.ereportbacken.model.dto.UserDepartmentMoveRequestDTO;
import com.pro.ereportbacken.model.dto.UserUpdateRequestDTO;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserOrganizationMoveStrategy;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class UserTransferCommandExecutor {

    private final UserService userService;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final AuthorizationAdministrationService authorizationService;
    private final SecurityHashingService hashingService;
    private final ObjectMapper objectMapper;

    public UserTransferCommandExecutor(
            UserService userService,
            UserRepository userRepository,
            TenantContext tenantContext,
            AuthorizationAdministrationService authorizationService,
            SecurityHashingService hashingService,
            ObjectMapper objectMapper) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.authorizationService = authorizationService;
        this.hashingService = hashingService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UserExecutionResult execute(UserTransferPlanService.UserExecutionPlan plan) {
        User user = plan.existingUserId() == null
                ? create(plan.userMutation())
                : requireCurrentUser(plan);
        UserTransferPlanService.UserMutation mutation = plan.userMutation();
        if (mutation != null && mutation.action() != UserTransferPlanService.UserAction.CREATE) {
            user = update(user, mutation);
        }
        if (!plan.authorizationOperations().isEmpty()) {
            AuthorizationPlanRequestDTO request = new AuthorizationPlanRequestDTO(
                    user.getAuthorizationVersion(), plan.authorizationOperations());
            var preview = authorizationService.createPlan(user.getId(), request);
            var applied = authorizationService.applyPlan(
                    user.getId(), preview.planToken(), idempotencyKey(user, plan));
            user.setAuthorizationVersion(applied.authorizationVersion());
        }
        return new UserExecutionResult(user.getId(), user.getAuthorizationVersion());
    }

    private User create(UserTransferPlanService.UserMutation mutation) {
        if (mutation == null || mutation.action() != UserTransferPlanService.UserAction.CREATE) {
            throw new IllegalStateException("不存在的用户必须在同一文件中提供 CREATE 命令。" );
        }
        UserCreateRequestDTO request = new UserCreateRequestDTO();
        request.setPhone(mutation.phone());
        request.setUsername(mutation.username());
        request.setPassword(mutation.initialPassword());
        request.setName(mutation.name());
        request.setEmail(mutation.email());
        request.setStatus(mutation.status());
        request.setOrgUnitId(mutation.orgUnitId());
        request.setDepartmentId(mutation.departmentId());
        return userService.createUser(request);
    }

    private User requireCurrentUser(UserTransferPlanService.UserExecutionPlan plan) {
        User user = userRepository.findByTenantIdAndId(tenantContext.requireTenantId(), plan.existingUserId())
                .filter(candidate -> candidate.getPhone().equals(plan.phone()))
                .orElseThrow(() -> new IllegalStateException("用户在执行前已被删除或唯一标识发生变化。" ));
        if (!Objects.equals(user.getAuthorizationVersion(), plan.beforeAuthorizationVersion())) {
            throw new IllegalStateException("用户授权版本在执行前发生变化，请重新预览。" );
        }
        return user;
    }

    private User update(User user, UserTransferPlanService.UserMutation mutation) {
        if (mutation.action() == UserTransferPlanService.UserAction.ORG_MOVE) {
            return userService.updateUserDetails(
                    user.getId(), details(user, user.getName(), user.getEmail(), mutation.orgUnitId(),
                            mutation.moveStrategy()));
        }
        if (mutation.action() == UserTransferPlanService.UserAction.DEPARTMENT_MOVE) {
            return userService.moveDepartment(user.getId(),
                    new UserDepartmentMoveRequestDTO(
                            mutation.departmentId(), user.getAuthorizationVersion()));
        }
        if (mutation.name() != null || mutation.emailSpecified()) {
            user = userService.updateUserDetails(
                    user.getId(),
                    details(user,
                            mutation.name() == null ? user.getName() : mutation.name(),
                            mutation.emailSpecified() ? mutation.email() : user.getEmail(),
                            user.getOrgUnitId(), null));
        }
        if (mutation.status() != null && mutation.status() != user.getStatus()) {
            user = userService.updateUserStatus(user.getId(), mutation.status().name());
        }
        return user;
    }

    private UserUpdateRequestDTO details(User user, String name, String email, UUID orgUnitId,
                                         UserOrganizationMoveStrategy moveStrategy) {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setName(name);
        request.setPhone(user.getPhone());
        request.setEmail(email);
        request.setOrgUnitId(orgUnitId);
        if (!Objects.equals(orgUnitId, user.getOrgUnitId())) {
            request.setOrganizationMoveStrategy(moveStrategy == null
                    ? UserOrganizationMoveStrategy.KEEP : moveStrategy);
            request.setExpectedAuthorizationVersion(user.getAuthorizationVersion());
        }
        return request;
    }

    private String idempotencyKey(User user, UserTransferPlanService.UserExecutionPlan plan) {
        try {
            String digest = hashingService.sha256(objectMapper.writeValueAsString(plan.authorizationOperations()));
            return "user-import-" + user.getId() + "-" + user.getAuthorizationVersion() + "-" + digest.substring(0, 16);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("用户授权导入命令无法生成幂等键。", exception);
        }
    }

    public record UserExecutionResult(UUID userId, Long authorizationVersion) {
    }
}
