package com.devmh.graphs.v2;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.time.LocalDateTime;

@Node
@Data
public class Case {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String uuid;

    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Case() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Case(String name) {
        this();
        this.name = name;
    }

}
