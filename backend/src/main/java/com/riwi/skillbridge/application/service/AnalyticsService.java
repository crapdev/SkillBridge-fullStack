package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.GetAnalyticsUseCase;
import com.riwi.skillbridge.application.port.out.AnalyticsReaderPort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class AnalyticsService implements GetAnalyticsUseCase {
    private final AnalyticsReaderPort reader;

    public AnalyticsService(AnalyticsReaderPort reader) {
        this.reader = reader;
    }

    @Override
    public Optional<Map<String, Object>> results(Dataset dataset) {
        return reader.read(dataset);
    }
}
