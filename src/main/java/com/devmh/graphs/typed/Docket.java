package com.devmh.graphs.typed;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Node("Docket")
public class Docket {
    @Id
    @GeneratedValue(UUIDStringGenerator.class)
    private String id;

    private String number;

    @Relationship(type = "CONTAINS")
    @Builder.Default
    private Set<Case> cases = new HashSet<>();
}
