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
import java.util.Collection;
import java.util.EnumSet;
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
    public Page<UserAccount> listUsers(Role role, String query, Collection<AccountStatus> statuses, Pageable pageable) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot list ADMIN users");
        }
        String q = query != null && !query.trim().isEmpty() ? query.trim() : "";
        return users.findUsers(role, q, statuses, pageable);
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
        stats.put("providers", roleStats(Role.PROVIDER, startOfMonth));
        stats.put("customers", roleStats(Role.CUSTOMER, startOfMonth));
        return stats;
    }

    private Map<String, Long> roleStats(Role role, Instant startOfMonth) {
        Map<String, Long> values = new HashMap<>();
        values.put("total", users.countByRole(role));
        values.put("newThisMonth", users.countByRoleAndCreatedAtGreaterThanEqual(role, startOfMonth));
        values.put("active", users.countByRoleAndStatusIn(role, EnumSet.of(AccountStatus.ACTIVE)));
        values.put("pending", users.countByRoleAndStatusIn(role, EnumSet.of(AccountStatus.PENDING_APPROVAL)));
        // "Inactivos" agrupa las cuentas desactivadas y las solicitudes rechazadas: ninguna puede iniciar sesión
        values.put("inactive", users.countByRoleAndStatusIn(role, EnumSet.of(AccountStatus.INACTIVE, AccountStatus.REJECTED)));
        return values;
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

    /**
     * Transiciones permitidas:
     * - ACTIVE: aprobar un proveedor pendiente o rechazado, o reactivar una cuenta desactivada.
     * - REJECTED: solo para solicitudes de proveedor pendientes.
     * - INACTIVE: solo para cuentas activas.
     * Ninguna cuenta vuelve a PENDING_APPROVAL: ese estado solo lo asigna el registro de proveedores.
     */
    @Override
    public UserAccount changeStatus(UUID id, AccountStatus status) {
        UserAccount existing = getUserById(id);
        if (existing == null) {
            return null; // Will trigger 404 in controller
        }
        if (existing.status() == status) {
            return existing;
        }
        switch (status) {
            case PENDING_APPROVAL -> throw new BusinessRuleException("Una cuenta no puede volver a quedar pendiente de aprobación");
            case REJECTED -> {
                if (existing.status() != AccountStatus.PENDING_APPROVAL) {
                    throw new BusinessRuleException("Solo se pueden rechazar solicitudes de proveedor pendientes");
                }
            }
            case INACTIVE -> {
                if (existing.status() != AccountStatus.ACTIVE) {
                    throw new BusinessRuleException("Solo se pueden desactivar cuentas activas");
                }
            }
            case ACTIVE -> { /* aprobar o reactivar: permitido desde cualquier otro estado */ }
        }
        return users.save(new UserAccount(existing.id(), existing.name(), existing.email(), existing.passwordHash(),
                existing.role(), status, existing.createdAt()));
    }
}
