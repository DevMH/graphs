package com.devmh.graphs.v1;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;

@Node("TeamRel")
public class TeamRelationshipNode {
    @Id
    private String id;
    private String caseId;
    private String versionId;
    private String kind; // "REPORTS_TO", future-safe for "ADVISES", etc.
    private Instant validFrom; // optional
    private Instant validTo; // optional

    @Relationship(type = "FROM", direction = Relationship.Direction.OUTGOING)
    private TeamNode from;

    @Relationship(type = "TO", direction = Relationship.Direction.OUTGOING)
    private TeamNode to;
}
