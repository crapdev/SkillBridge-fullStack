package com.riwi.skillbridge.application.port.out;

public interface TokenPort {
    String generate(String username, String role);
    String extractUsername(String token);
    boolean isTokenValid(String token, String username);
}
