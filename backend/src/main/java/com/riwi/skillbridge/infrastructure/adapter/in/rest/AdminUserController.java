package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AdminManageUsersUseCase;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AdminCreateUserRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AdminUpdateUserRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AdminUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminManageUsersUseCase adminManageUsersUseCase;

    public AdminUserController(AdminManageUsersUseCase adminManageUsersUseCase) {
        this.adminManageUsersUseCase = adminManageUsersUseCase;
    }

    @GetMapping
    public Page<AdminUser> listUsers(
            @RequestParam Role role,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot list ADMIN users");
        }
        return adminManageUsersUseCase.listUsers(role, q, pageable)
                .map(AdminUser::fromDomain);
    }

    @GetMapping("/stats")
    public Map<String, Map<String, Long>> getStats() {
        return adminManageUsersUseCase.getStats();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUser> getUserById(@PathVariable UUID id) {
        UserAccount user = adminManageUsersUseCase.getUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AdminUser.fromDomain(user));
    }

    @PostMapping
    public ResponseEntity<AdminUser> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot create ADMIN users");
        }
        UserAccount created = adminManageUsersUseCase.createUser(
                request.name(), request.email(), request.password(), request.role());
        AdminUser response = AdminUser.fromDomain(created);
        
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
                
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUser> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot update to ADMIN role");
        }
        UserAccount updated = adminManageUsersUseCase.updateUser(id, request.name(), request.email(), request.role());
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AdminUser.fromDomain(updated));
    }
}
