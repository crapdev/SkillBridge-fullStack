package com.riwi.skillbridge.domain.model;

/**
 * Estado de una cuenta. Los clientes nacen activos; los proveedores esperan
 * la aprobación de un administrador antes de poder iniciar sesión.
 */
public enum AccountStatus {
    ACTIVE,
    PENDING_APPROVAL,
    REJECTED
}
