package com.devmh.graphs.v1;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CaseVersionGraphPatchService {

    private final Neo4jClient client;

    public CaseVersionGraphPatchService(Neo4jClient client) {
        this.client = client;
    }

    @Transactional
    public void applyDelta(String caseId, String versionId, GraphDelta delta) {
        // Team additions
        for (var teamId : delta.addTeams()) {
            client.query("""
                      MATCH (v:CaseVersion {versionId: $versionId})
                      MATCH (t:Team {teamId: $teamId})
                      MERGE (v)-[:ASSIGNED_TEAM]->(t)
                    """).bindAll(Map.of("versionId", versionId, "teamId", teamId)).run();
        }

        // Team removals
        for (var teamId : delta.removeTeams()) {
            client.query("""
                      MATCH (v:CaseVersion {versionId: $versionId})-[r:ASSIGNED_TEAM]->(t:Team {teamId: $teamId})
                      DELETE r
                    """).bindAll(Map.of("versionId", versionId, "teamId", teamId)).run();
        }

        // Edge additions
        for (var e : delta.addEdges()) {
            var id = (e.id() == null || e.id().isBlank()) ? java.util.UUID.randomUUID().toString() : e.id();
            client.query("""
                      MATCH (v:CaseVersion {versionId: $versionId})
                      MATCH (f:Team {teamId: $from}), (t:Team {teamId: $to})
                      MERGE (tr:TeamRel {id: $id})
                        ON CREATE SET tr.caseId = $caseId, tr.versionId = $versionId, tr.kind = $kind
                      MERGE (v)-[:TEAM_REL]->(tr)
                      MERGE (tr)-[:FROM]->(f)
                      MERGE (tr)-[:TO]->(t)
                    """).bindAll(Map.of(
                    "caseId", caseId,
                    "versionId", versionId,
                    "id", id,
                    "from", e.from(),
                    "to", e.to(),
                    "kind", e.kind()
            )).run();
        }

        // Edge removals (prefer id; fallback to composite)
        for (var e : delta.removeEdges()) {
            if (e.id() != null && !e.id().isBlank()) {
                client.query("""
                          MATCH (:CaseVersion {versionId: $versionId})-[:TEAM_REL]->(tr:TeamRel {id: $id})
                          DETACH DELETE tr
                        """).bindAll(Map.of("versionId", versionId, "id", e.id())).run();
            } else {
                client.query("""
                          MATCH (:CaseVersion {versionId: $versionId})-[:TEAM_REL]->(tr:TeamRel {caseId: $caseId, versionId: $versionId, kind: $kind})
                          MATCH (tr)-[:FROM]->(:Team {teamId: $from})
                          MATCH (tr)-[:TO]->(:Team {teamId: $to})
                          DETACH DELETE tr
                        """).bindAll(Map.of(
                        "caseId", caseId,
                        "versionId", versionId,
                        "from", e.from(),
                        "to", e.to(),
                        "kind", e.kind()
                )).run();
            }
        }
    }
}