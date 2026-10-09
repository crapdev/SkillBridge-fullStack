package com.riwi.skillbridge.domain.exception;

/** La cuenta existe y las credenciales son correctas, pero todavía no puede usarse. */
public class AccountNotActiveException extends RuntimeException {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
