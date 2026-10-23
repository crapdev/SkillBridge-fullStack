package com.riwi.skillbridge.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa los procesos programados (@Scheduled), como el que completa las reservas terminadas (BUG-05). */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
