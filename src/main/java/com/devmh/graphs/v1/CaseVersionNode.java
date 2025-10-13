package com.devmh.graphs.v1;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/*
    (:Case {caseId})-[:HAS_VERSION]->(:CaseVersion {versionId, asOf, createdAt})
    (:CaseVersion)-[:ASSIGNED_TEAM]->(:Team {teamId})

    (:CaseVersion)-[:TEAM_REL]->(:TeamRel {kind:'REPORTS_TO', caseId, versionId})
    (:TeamRel)-[:FROM]->(:Team)
    (:TeamRel)-[:TO]->(:Team)

    CREATE CONSTRAINT case_id IF NOT EXISTS
    FOR (c:Case) REQUIRE c.caseId IS UNIQUE;

    CREATE CONSTRAINT case_version_id IF NOT EXISTS
    FOR (v:CaseVersion) REQUIRE v.versionId IS UNIQUE;

    CREATE INDEX case_version_lookup IF NOT EXISTS
    FOR (v:CaseVersion) ON (v.caseId, v.asOf);

    CREATE CONSTRAINT team_id IF NOT EXISTS
    FOR (t:Team) REQUIRE t.teamId IS UNIQUE;

    CREATE INDEX team_relationship_lookup IF NOT EXISTS
    FOR (r:TeamRel) ON (r.caseId, r.versionId, r.kind);

    all Teams assigned at that version + all Team ↔ Team relations scoped to that version, and only among those Teams.

    MATCH (c:Case {caseId: $caseId})-[:HAS_VERSION]->(v:CaseVersion {versionId: $versionId})
    MATCH (v)-[:ASSIGNED_TEAM]->(t:Team)
    WITH v, collect(t) AS teams
    MATCH (v)-[:TEAM_REL]->(tr:TeamRel)-[:FROM]->(f:Team)
    MATCH (tr)-[:TO]->(to:Team)
    WHERE f IN teams AND to IN teams
    RETURN teams AS teams, collect({ from: f.teamId, to: to.teamId, kind: tr.kind, id: tr.id }) AS edges

    As-of timestamp → version

    MATCH (:Case {caseId: $caseId})-[:HAS_VERSION]->(v:CaseVersion)
    WHERE v.asOf <= $asOf
    RETURN v
    ORDER BY v.asOf DESC
    LIMIT 1

    Snapshotting

    Creating a snapshot is append-only:
        create a new CaseVersion node,
        link teams with (:CaseVersion)-[:ASSIGNED_TEAM]->(:Team),
        and add any TeamRel nodes for this version.
 */

@Node("CaseVersion")
public class CaseVersionNode {
    @Id
    private String versionId;
    private String caseId; // duplicate for convenience/indexing
    private Instant asOf; // asOf <= $ts
    private Instant createdAt;

    @Relationship(type = "ASSIGNED_TEAM", direction = Relationship.Direction.OUTGOING)
    private Set<TeamNode> teams = new HashSet<>();

    @Relationship(type = "TEAM_REL", direction = Relationship.Direction.OUTGOING)
    private Set<TeamRelationshipNode> teamRels = new HashSet<>();
}
