package ru.student.hotel.common;

import java.time.Instant;

public record ErrorDto(Instant timestamp, int status, String message, String traceId) {}
