package com.devmh.graphs.typed;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("Person")
public abstract class Person {
    @Id
    @GeneratedValue(UUIDStringGenerator.class)
    private String id;

    private String name;
}
