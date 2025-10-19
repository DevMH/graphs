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
public class CaseSnapshot {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String uuid;

    @Relationship(type = "SNAPSHOT_OF", direction = Relationship.Direction.OUTGOING)
    private Case sourceCase;

    @Relationship(type = "CONTAINS_CASE", direction = Relationship.Direction.INCOMING)
    private DocketVersion docketVersion;

    @Relationship(type = "RELATED_TO_CASE", direction = Relationship.Direction.OUTGOING)
    private Set<CaseSnapshot> relatedCaseSnapshots = new HashSet<>();

    private LocalDateTime snapshotAt;

    public CaseSnapshot() {
        this.snapshotAt = LocalDateTime.now();
    }

    public CaseSnapshot(Case sourceCase) {
        this();
        this.sourceCase = sourceCase;
    }

    public void addRelatedCaseSnapshot(CaseSnapshot relatedSnapshot) {
        this.relatedCaseSnapshots.add(relatedSnapshot);
    }
}
