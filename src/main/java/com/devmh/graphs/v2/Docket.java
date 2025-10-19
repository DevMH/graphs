package com.devmh.graphs.v2;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Node
@Data
public class Docket {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String uuid;

    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Relationship(type = "HAS_VERSION", direction = Relationship.Direction.OUTGOING)
    private Set<DocketVersion> versions = new HashSet<>();

    public Docket() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Docket(String name) {
        this();
        this.name = name;
    }
}
