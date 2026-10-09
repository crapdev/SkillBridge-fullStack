package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface AdminManageUsersUseCase {
    /** `statuses` vacío o nulo devuelve usuarios en cualquier estado. */
    Page<UserAccount> listUsers(Role role, String query, Collection<AccountStatus> statuses, Pageable pageable);
    Map<String, Map<String, Long>> getStats();
    UserAccount getUserById(UUID id);
    UserAccount createUser(String name, String email, String password, Role role);
    UserAccount updateUser(UUID id, String name, String email, Role role);
    /** Aprueba, rechaza, desactiva o reactiva una cuenta; devuelve null si el usuario no existe. */
    UserAccount changeStatus(UUID id, AccountStatus status);
}
