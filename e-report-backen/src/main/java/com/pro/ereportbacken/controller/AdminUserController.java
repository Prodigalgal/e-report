package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.PasswordResetRequestDTO;
import com.pro.ereportbacken.model.dto.UserCreateRequestDTO;
import com.pro.ereportbacken.model.dto.UserResponseDTO;
import com.pro.ereportbacken.model.dto.UserUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.UserDetailResponseDTO;
import com.pro.ereportbacken.model.dto.UserAdministrationUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.PageResponseDTO;
import com.pro.ereportbacken.model.dto.UserOrganizationMovePreviewDTO;
import com.pro.ereportbacken.model.dto.UserOrganizationMovePreviewRequestDTO;
import com.pro.ereportbacken.model.dto.UserDepartmentMoveRequestDTO;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.UserService;
import com.pro.ereportbacken.service.UserOrganizationMovePreviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final UserOrganizationMovePreviewService organizationMovePreviewService;

    public AdminUserController(
            UserService userService,
            UserOrganizationMovePreviewService organizationMovePreviewService) {
        this.userService = userService;
        this.organizationMovePreviewService = organizationMovePreviewService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "')")
    public ResponseEntity<PageResponseDTO<UserResponseDTO>> getAllUsers(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
            @PageableDefault(size = 10, sort = "username", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(userService.findAllUserResponses(
                searchTerm, orgUnitId, departmentId, status, pageable, includeDeleted)));
    }

    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_RESTORE + "')")
    public ResponseEntity<UserResponseDTO> restoreUser(@PathVariable UUID userId) {
        User restoredUser = userService.restoreUser(userId);
        return ResponseEntity.ok(userService.toUserResponseDTO(restoredUser));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "')")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable UUID userId) {
        User user = userService.findUserById(userId);
        return ResponseEntity.ok(userService.toUserResponseDTO(user));
    }

    @GetMapping("/{userId}/detail")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "')")
    public ResponseEntity<UserDetailResponseDTO> getUserDetail(@PathVariable UUID userId) {
        User user = userService.findUserById(userId);
        return ResponseEntity.ok(toUserDetail(user));
    }

    @PutMapping("/{userId}/detail")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "') and hasAnyAuthority('"
            + PermissionCatalog.USER_MANAGE_UPDATE_DETAILS + "','"
            + PermissionCatalog.USER_MANAGE_CONTACT_UPDATE + "','"
            + PermissionCatalog.USER_MANAGE_STATUS_ACTIVATE + "','"
            + PermissionCatalog.USER_MANAGE_STATUS_DEACTIVATE + "','"
            + PermissionCatalog.USER_MANAGE_STATUS_LOCK + "','"
            + PermissionCatalog.USER_MANAGE_STATUS_UNLOCK + "','"
            + PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE + "','"
            + PermissionCatalog.USER_AUTHORIZATION_GRANT + "','"
            + PermissionCatalog.USER_AUTHORIZATION_ADJUST + "','"
            + PermissionCatalog.USER_AUTHORIZATION_REVOKE + "')")
    public ResponseEntity<UserDetailResponseDTO> updateUserDetail(
            @PathVariable UUID userId,
            @Valid @RequestBody UserAdministrationUpdateRequestDTO request) {
        return ResponseEntity.ok(toUserDetail(userService.updateAdministration(userId, request)));
    }

    @PostMapping("/{userId}/organization-move/preview")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "') and hasAuthority('"
            + PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE + "')")
    public ResponseEntity<UserOrganizationMovePreviewDTO> previewOrganizationMove(
            @PathVariable UUID userId,
            @Valid @RequestBody UserOrganizationMovePreviewRequestDTO request) {
        return ResponseEntity.ok(organizationMovePreviewService.preview(userId, request));
    }

    @PostMapping("/{userId}/organization-move")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "') and hasAuthority('"
            + PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE + "')")
    public ResponseEntity<UserDetailResponseDTO> moveOrganization(
            @PathVariable UUID userId,
            @Valid @RequestBody UserOrganizationMovePreviewRequestDTO request) {
        return ResponseEntity.ok(toUserDetail(userService.moveOrganization(userId, request)));
    }

    @PostMapping("/{userId}/department-move")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "') and hasAuthority('"
            + PermissionCatalog.USER_MANAGE_DEPARTMENT_MOVE + "')")
    public ResponseEntity<UserDetailResponseDTO> moveDepartment(
            @PathVariable UUID userId,
            @Valid @RequestBody UserDepartmentMoveRequestDTO request) {
        return ResponseEntity.ok(toUserDetail(userService.moveDepartment(userId, request)));
    }

    private UserDetailResponseDTO toUserDetail(User user) {
        return new UserDetailResponseDTO(
                userService.toUserResponseDTO(user),
                userService.getWorkOrderPerformance(user),
                userService.managementCapabilities(user)
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_CREATE + "')")
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserCreateRequestDTO userCreateRequestDTO) {
        User createdUser = userService.createUser(userCreateRequestDTO);
        return new ResponseEntity<>(userService.toUserResponseDTO(createdUser), HttpStatus.CREATED);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.USER_MANAGE_UPDATE_DETAILS + "','"
            + PermissionCatalog.USER_MANAGE_CONTACT_UPDATE + "','"
            + PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE + "')")
    public ResponseEntity<UserResponseDTO> updateUserDetails(@PathVariable UUID userId,
                                                             @Valid @RequestBody UserUpdateRequestDTO userUpdateRequestDTO) {
        User updatedUser = userService.updateUserDetails(userId, userUpdateRequestDTO);
        return ResponseEntity.ok(userService.toUserResponseDTO(updatedUser));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.USER_MANAGE_STATUS_ACTIVATE + "','"
            + PermissionCatalog.USER_MANAGE_STATUS_DEACTIVATE + "','"
            + PermissionCatalog.USER_MANAGE_STATUS_LOCK + "','"
            + PermissionCatalog.USER_MANAGE_STATUS_UNLOCK + "')")
    public ResponseEntity<UserResponseDTO> updateUserStatus(@PathVariable UUID userId, @RequestParam String status) {
        User updatedUser = userService.updateUserStatus(userId, status);
        return ResponseEntity.ok(userService.toUserResponseDTO(updatedUser));
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_RESET_PASSWORD + "')")
    public ResponseEntity<Void> adminResetPassword(@PathVariable UUID userId,
                                                   @Valid @RequestBody PasswordResetRequestDTO passwordResetRequestDTO) {
        userService.adminResetPassword(userId, passwordResetRequestDTO.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_DELETE + "')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
