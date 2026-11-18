package com.riwi.skillbridge.application.port.in;

import java.util.Map;
import java.util.Optional;

public interface GetAnalyticsUseCase {
    enum Dataset { SNAPSHOT, FIXTURES }

    Optional<Map<String, Object>> results(Dataset dataset);
}
