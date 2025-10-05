package com.devmh.graphs.generic;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Node
public class GenericNode {
    @Id
    @GeneratedValue(UUIDStringGenerator.class)
    private String id;

    @DynamicLabels
    @Builder.Default
    private List<String> labels = new ArrayList<>();

    /** Arbitrary properties stored under their own keys (no prefix). */
    @CompositeProperty
    @Builder.Default
    private Map<String, Object> props = new HashMap<>();
}
