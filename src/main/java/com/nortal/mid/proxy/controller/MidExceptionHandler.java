package com.nortal.mid.proxy.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class MidExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse genericException(Exception exception) {
        log.error("Internal server error - {}", exception.getMessage(), exception);
        return new ErrorResponse(exception.getMessage());
    }

    @Getter
    @RequiredArgsConstructor
    public static class ErrorResponse {
        private final String error;
        private final LocalDateTime time = LocalDateTime.now();
    }
}
