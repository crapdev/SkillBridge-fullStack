package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

// Le devolveremos a Angular el secreto para que abra el formulario seguro
public record PaymentSecretResponse(
    String clientSecret
) {}
