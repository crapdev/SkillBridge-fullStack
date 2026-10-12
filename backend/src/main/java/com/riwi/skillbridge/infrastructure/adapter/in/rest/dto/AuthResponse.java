package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

/**
 * Respuesta de login y registro. La sesión viaja en la cookie HttpOnly; token y tokenType se mantienen
 * solo mientras el frontend migra a la cookie y se eliminan en SCRUM-227 (HU-29).
 */
public record AuthResponse(String token, String tokenType, SessionUserResponse user) {}
