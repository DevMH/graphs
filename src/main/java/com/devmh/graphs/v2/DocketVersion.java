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
public class DocketVersion {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String uuid;

    private Integer versionNumber;

    private String description;

    private LocalDateTime createdAt;

    private Boolean isActive;

    @Relationship(type = "HAS_VERSION", direction = Relationship.Direction.INCOMING)
    private Docket docket;

    @Relationship(type = "CONTAINS_CASE", direction = Relationship.Direction.OUTGOING)
    private Set<CaseSnapshot> caseSnapshots = new HashSet<>();

    public DocketVersion() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
    }

    public DocketVersion(Integer versionNumber, String description) {
        this();
        this.versionNumber = versionNumber;
        this.description = description;
    }
}
