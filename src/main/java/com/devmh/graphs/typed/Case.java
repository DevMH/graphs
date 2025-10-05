package com.devmh.graphs.typed;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Node("Case")
public class Case {
    @Id
    @GeneratedValue(UUIDStringGenerator.class)
    private String id;

    private String name;

    @Relationship(type = "ASSIGNED_TO")
    @Builder.Default
    private Set<Person> assignees = new HashSet<>();

    @Relationship(type = "REVIEWS")
    @Builder.Default
    private Set<Person> reviewers = new HashSet<>();
}
