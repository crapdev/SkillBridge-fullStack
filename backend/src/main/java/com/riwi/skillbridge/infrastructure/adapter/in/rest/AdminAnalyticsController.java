package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GetAnalyticsUseCase;
import com.riwi.skillbridge.application.port.in.GetAnalyticsUseCase.Dataset;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {
    private final GetAnalyticsUseCase analytics;

    public AdminAnalyticsController(GetAnalyticsUseCase analytics) {
        this.analytics = analytics;
    }

    @GetMapping
    public ResponseEntity<?> results(@RequestParam(required = false) String dataset) {
        Dataset selection = switch (dataset) {
            case null -> Dataset.SNAPSHOT;
            case "snapshot" -> Dataset.SNAPSHOT;
            case "fixtures" -> Dataset.FIXTURES;
            default -> throw new IllegalArgumentException("dataset debe ser snapshot o fixtures");
        };

        var results = analytics.results(selection);
        if (results.isEmpty()) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "Aún no hay resultados del pipeline de datos")).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(results.get());
    }
}
