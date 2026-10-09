package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AdminManageUsersUseCase;
import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUserService implements AdminManageUsersUseCase {

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwords;

    public AdminUserService(UserRepositoryPort users, PasswordHasherPort passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    public Page<UserAccount> listUsers(Role role, String query, Pageable pageable) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot list ADMIN users");
        }
        String q = query != null && !query.trim().isEmpty() ? query.trim() : "";
        return users.findUsers(role, q, pageable);
    }

    @Override
    public Map<String, Map<String, Long>> getStats() {
        ZonedDateTime firstDayOfMonth = ZonedDateTime.now(ZoneId.of("America/Bogota"))
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        Instant startOfMonth = firstDayOfMonth.toInstant();

        Map<String, Map<String, Long>> stats = new HashMap<>();

        Map<String, Long> providers = new HashMap<>();
        providers.put("total", users.countByRole(Role.PROVIDER));
        providers.put("newThisMonth", users.countByRoleAndCreatedAtGreaterThanEqual(Role.PROVIDER, startOfMonth));
        stats.put("providers", providers);

        Map<String, Long> customers = new HashMap<>();
        customers.put("total", users.countByRole(Role.CUSTOMER));
        customers.put("newThisMonth", users.countByRoleAndCreatedAtGreaterThanEqual(Role.CUSTOMER, startOfMonth));
        stats.put("customers", customers);

        return stats;
    }

    @Override
    public UserAccount getUserById(UUID id) {
        UserAccount user = users.findById(id).orElse(null);
        if (user == null || user.role() == Role.ADMIN) {
            return null; // Controller should return 404
        }
        return user;
    }

    @Override
    public UserAccount createUser(String name, String email, String password, Role role) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot create ADMIN users");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (users.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException("El correo ya está registrado");
        }
        // Una cuenta creada por el administrador ya está aprobada: nace activa, también si es de proveedor
        return users.save(new UserAccount(
                UUID.randomUUID(), name.trim(), normalizedEmail, passwords.encode(password), role, AccountStatus.ACTIVE, null));
    }

    @Override
    public UserAccount updateUser(UUID id, String name, String email, Role role) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot change role to ADMIN");
        }
        UserAccount existing = getUserById(id);
        if (existing == null) {
            return null; // Will trigger 404 in controller
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (!existing.email().equals(normalizedEmail) && users.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException("El correo ya está registrado");
        }
        
        // Editar datos no cambia el estado de la cuenta (pendiente, activa o rechazada)
        return users.save(new UserAccount(
                existing.id(), name.trim(), normalizedEmail, existing.passwordHash(), role, existing.status(), existing.createdAt()));
    }
}
