package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GetProviderStatsUseCase;
import com.riwi.skillbridge.application.port.in.GetProviderStatsUseCase.ProviderStats;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU-22: indicadores del dashboard del proveedor. Solo PROVIDER (regla /api/provider/** de SecurityConfiguration). */
@RestController
public class ProviderStatsController {
    private final GetProviderStatsUseCase stats;

    public ProviderStatsController(GetProviderStatsUseCase stats) {
        this.stats = stats;
    }

    @GetMapping("/api/provider/stats")
    public ProviderStats myStats(Authentication authentication) {
        return stats.statsFor(authentication.getName());
    }
}
