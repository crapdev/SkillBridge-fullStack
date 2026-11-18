package com.riwi.skillbridge.infrastructure.adapter.out.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PostgresAnalyticsReader {
    private final JdbcTemplate jdbc;

    public PostgresAnalyticsReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> read() {
        var estados = jdbc.queryForList("""
                SELECT status, count(*) AS reservas
                FROM bookings
                GROUP BY status
                ORDER BY status
                """);
        var cancelaciones = jdbc.queryForList("""
                SELECT o.provider_id::text AS provider_id,
                       'prov_' || right(o.provider_id::text, 2) AS provider_name,
                       count(*) AS reservas,
                       count(*) FILTER (WHERE b.status = 'CANCELLED') AS canceladas,
                       round(100.0 * count(*) FILTER (WHERE b.status = 'CANCELLED') / count(*), 2)
                           AS tasa_cancelacion
                FROM bookings b
                JOIN offerings o ON o.id = b.offering_id
                GROUP BY o.provider_id
                ORDER BY o.provider_id
                """);
        var completitud = List.of(
                completeness("users", "app_users", "id", "role", "status", "created_at"),
                completeness("offerings", "offerings", "id", "provider_id", "title", "category", "price",
                        "level", "duration_minutes", "review_status", "active", "created_at"),
                completeness("bookings", "bookings", "id", "offering_id", "customer_id", "scheduled_at",
                        "status", "created_at", "price"),
                completeness("availability_slots", "availability_slots", "id", "offering_id", "scheduled_at",
                        "reserved"),
                completeness("payments", "payments", "id", "booking_id", "amount", "currency", "status",
                        "created_at"),
                completeness("reviews", "reviews", "id", "booking_id", "rating", "created_at"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataset", "snapshot");
        payload.put("generatedAt", Instant.now().toString());
        payload.put("sparkVersion", null);
        payload.put("source", "PostgreSQL");
        payload.put("reservasPorEstado", estados);
        payload.put("tasaCancelacion", cancelaciones);
        payload.put("completitud", completitud);
        payload.put("proximas", List.of());
        return payload;
    }

    // Los nombres de tabla y columna provienen del contrato fijo, nunca de la petición.
    private Map<String, Object> completeness(String label, String table, String... requiredColumns) {
        List<String> columns = List.of(requiredColumns);
        String missing = columns.stream().map(column -> column + " IS NULL")
                .collect(Collectors.joining(" OR "));
        var counts = jdbc.queryForMap("""
                SELECT count(*) AS filas, count(*) FILTER (WHERE %s) AS filas_con_nulos
                FROM %s
                """.formatted(missing, table));
        return Map.of("tabla", label, "columnas", columns, "filas", counts.get("filas"),
                "filas_con_nulos", counts.get("filas_con_nulos"));
    }
}
