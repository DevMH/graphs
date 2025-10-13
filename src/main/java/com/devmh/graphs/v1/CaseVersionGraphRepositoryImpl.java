package com.devmh.graphs.v1;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
class CaseVersionGraphRepositoryImpl implements CaseVersionGraphRepository {
    private final Neo4jClient client;

    public CaseVersionGraphRepositoryImpl(Neo4jClient client) { this.client = client; }

    @Override
    public CaseVersionGraph loadGraph(String caseId, String versionId) {
        var cypher = """
          MATCH (c:Case {caseId: $caseId})-[:HAS_VERSION]->(v:CaseVersion {versionId: $versionId})
          MATCH (v)-[:ASSIGNED_TEAM]->(t:Team)
          WITH v, collect(t.teamId) AS teamIds, collect(t) AS teams
          MATCH (v)-[:TEAM_REL]->(tr:TeamRel)-[:FROM]->(f:Team)
          MATCH (tr)-[:TO]->(to:Team)
          WHERE f IN teams AND to IN teams
          RETURN teamIds AS teamIds,
                 collect({id: tr.id, from: f.teamId, to: to.teamId, kind: tr.kind}) AS edges
        """;

        var row = client.query(cypher)
                .bind(caseId).to("caseId")
                .bind(versionId).to("versionId")
                .fetch().one().orElse(Map.of("teamIds", List.of(), "edges", List.of()));

        @SuppressWarnings("unchecked")
        var teamIds = (List<String>) row.get("teamIds");
        @SuppressWarnings("unchecked")
        var edgesRaw = (List<Map<String, Object>>) row.get("edges");

        var edges = edgesRaw.stream()
                .map(m -> new EdgeView(
                        (String) m.get("id"),
                        (String) m.get("from"),
                        (String) m.get("to"),
                        (String) m.get("kind")))
                .toList();

        return new CaseVersionGraph(teamIds, edges);
    }
}
