package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.application.service.BusinessTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ZoneId businessZone(@Value("${app.timezone}") String timezone) {
        return ZoneId.of(timezone);
    }

    @Bean
    public BusinessTime businessTime(Clock clock, ZoneId businessZone) {
        return new BusinessTime(clock, businessZone);
    }
}
