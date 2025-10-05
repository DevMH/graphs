package com.devmh.graphs.generic;

import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericRelationship {
    private Integer fromIndex; // index into GenericGraph.nodes

    private Integer toIndex; // index into GenericGraph.nodes

    private String type; // e.g. ASSIGNED_TO, REVIEWS, CONTAINS

    @Builder.Default
    private Map<String,Object> props = new LinkedHashMap<>();
}
