package com.devmh.graphs.v1;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("Case")
public class CaseNode {
    @Id
    private String caseId;

    @Relationship(type = "HAS_VERSION", direction = Relationship.Direction.OUTGOING)
    private Set<CaseVersionNode> versions = new HashSet<>();
}
