package com.aurapet.backend.application.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageMetadata {
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
