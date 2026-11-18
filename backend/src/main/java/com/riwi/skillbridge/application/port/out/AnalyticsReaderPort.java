package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.application.port.in.GetAnalyticsUseCase.Dataset;

import java.util.Map;
import java.util.Optional;

public interface AnalyticsReaderPort {
    Optional<Map<String, Object>> read(Dataset dataset);
}
