package com.riwi.skillbridge.infrastructure.adapter.out.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.in.GetAnalyticsUseCase.Dataset;
import com.riwi.skillbridge.application.port.out.AnalyticsReaderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Component
public class AnalyticsReaderAdapter implements AnalyticsReaderPort {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final Path outputDirectory;
    private final PostgresAnalyticsReader postgres;

    public AnalyticsReaderAdapter(ObjectMapper objectMapper,
                                  @Value("${ANALYTICS_OUTPUT_DIR:../data/output}") String outputDirectory,
                                  PostgresAnalyticsReader postgres) {
        this.objectMapper = objectMapper;
        this.outputDirectory = Path.of(outputDirectory);
        this.postgres = postgres;
    }

    @Override
    public Optional<Map<String, Object>> read(Dataset dataset) {
        if (dataset == Dataset.SNAPSHOT) {
            return Optional.of(postgres.read());
        }
        try (var input = Files.newInputStream(outputDirectory.resolve("analytics-fixtures.json"))) {
            return Optional.of(objectMapper.readValue(input, PAYLOAD_TYPE));
        } catch (NoSuchFileException ex) {
            return Optional.empty();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudieron leer los resultados del pipeline de datos", ex);
        }
    }
}
