package com.aurapet.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private Instant timestamp;
    private T data;
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .timestamp(Instant.now())
                .data(data)
                .errors(new ArrayList<>())
                .build();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, "Operation completed successfully");
    }

    public static <T> ApiResponse<T> error(String message, List<String> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .data(null)
                .errors(errors != null ? errors : new ArrayList<>())
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        List<String> errors = new ArrayList<>();
        errors.add(message);
        return error(message, errors);
    }
}
