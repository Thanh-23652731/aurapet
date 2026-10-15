package com.aurapet.backend.application.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetPageData {
    private List<PetDto> items;
    private PageMetadata page;
}
