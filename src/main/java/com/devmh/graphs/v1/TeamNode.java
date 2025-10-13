package com.devmh.graphs.v1;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Team")
public class TeamNode {
    @Id
    private String teamId;
    private String name;
}
