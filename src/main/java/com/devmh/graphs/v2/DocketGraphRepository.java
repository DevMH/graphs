package com.devmh.graphs.v2;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class DocketGraphRepository {

    private final Neo4jClient neo4jClient;

    public DocketGraphRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * Efficiently retrieves the complete graph for a specific docket version
     * Returns all case snapshots and their relationships
     */
    @Transactional(readOnly = true)
    public DocketGraphDTO getDocketVersionGraph(String docketUuid, Integer versionNumber) {
        String query = """
            MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion {versionNumber: $versionNumber})
            OPTIONAL MATCH (dv)-[:CONTAINS_CASE]->(cs:CaseSnapshot)-[:SNAPSHOT_OF]->(c:Case)
            OPTIONAL MATCH (cs)-[r:RELATED_TO_CASE]->(relatedCs:CaseSnapshot)-[:SNAPSHOT_OF]->(relatedC:Case)
            RETURN d.uuid as docketUuid, 
                   d.name as docketName,
                   dv.uuid as versionUuid,
                   dv.versionNumber as versionNumber,
                   dv.description as versionDescription,
                   collect(DISTINCT {
                       snapshotUuid: cs.uuid,
                       caseUuid: c.uuid,
                       caseName: c.name
                   }) as cases,
                   collect(DISTINCT {
                       fromSnapshotUuid: cs.uuid,
                       fromCaseUuid: c.uuid,
                       toSnapshotUuid: relatedCs.uuid,
                       toCaseUuid: relatedC.uuid
                   }) as relationships
            """;

        Map<String, Object> result = neo4jClient.query(query)
                .bind(docketUuid).to("docketUuid")
                .bind(versionNumber).to("versionNumber")
                .fetch()
                .one()
                .orElse(Collections.emptyMap());

        return buildGraphDTO(result);
    }

    /**
     * Get statistics about a docket version
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDocketVersionStatistics(String docketUuid, Integer versionNumber) {
        String query = """
            MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion {versionNumber: $versionNumber})
            OPTIONAL MATCH (dv)-[:CONTAINS_CASE]->(cs:CaseSnapshot)
            OPTIONAL MATCH (cs)-[r:RELATED_TO_CASE]->(:CaseSnapshot)
            RETURN count(DISTINCT cs) as caseCount,
                   count(r) as relationshipCount
            """;

        return neo4jClient.query(query)
                .bind(docketUuid).to("docketUuid")
                .bind(versionNumber).to("versionNumber")
                .fetch()
                .one()
                .orElse(Map.of("caseCount", 0L, "relationshipCount", 0L));
    }

    /**
     * Compare two versions of a docket to see what changed
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareDocketVersions(
            String docketUuid,
            Integer version1,
            Integer version2) {

        String query = """
            MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv1:DocketVersion {versionNumber: $version1})
            MATCH (d)-[:HAS_VERSION]->(dv2:DocketVersion {versionNumber: $version2})
            
            OPTIONAL MATCH (dv1)-[:CONTAINS_CASE]->(cs1:CaseSnapshot)-[:SNAPSHOT_OF]->(c1:Case)
            OPTIONAL MATCH (dv2)-[:CONTAINS_CASE]->(cs2:CaseSnapshot)-[:SNAPSHOT_OF]->(c2:Case)
            
            WITH collect(DISTINCT c1.uuid) as cases1, collect(DISTINCT c2.uuid) as cases2
            
            RETURN [uuid IN cases1 WHERE NOT uuid IN cases2] as removedCases,
                   [uuid IN cases2 WHERE NOT uuid IN cases1] as addedCases,
                   [uuid IN cases1 WHERE uuid IN cases2] as unchangedCases
            """;

        return neo4jClient.query(query)
                .bind(docketUuid).to("docketUuid")
                .bind(version1).to("version1")
                .bind(version2).to("version2")
                .fetch()
                .one()
                .orElse(Collections.emptyMap());
    }

    private DocketGraphDTO buildGraphDTO(Map<String, Object> result) {
        DocketGraphDTO dto = new DocketGraphDTO();
        dto.setDocketUuid((String) result.get("docketUuid"));
        dto.setDocketName((String) result.get("docketName"));
        dto.setVersionUuid((String) result.get("versionUuid"));
        dto.setVersionNumber((Integer) result.get("versionNumber"));
        dto.setVersionDescription((String) result.get("versionDescription"));

        // Process cases
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> casesRaw = (List<Map<String, Object>>) result.get("cases");
        if (casesRaw != null) {
            List<CaseNode> cases = casesRaw.stream()
                    .filter(c -> c.get("caseUuid") != null)
                    .map(c -> {
                        CaseNode node = new CaseNode();
                        node.setSnapshotUuid((String) c.get("snapshotUuid"));
                        node.setUuid((String) c.get("caseUuid"));
                        node.setName((String) c.get("caseName"));
                        return node;
                    })
                    .collect(Collectors.toList());
            dto.setCases(cases);
        }

        // Process relationships
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relsRaw = (List<Map<String, Object>>) result.get("relationships");
        if (relsRaw != null) {
            List<CaseRelationship> relationships = relsRaw.stream()
                    .filter(r -> r.get("fromCaseUuid") != null && r.get("toCaseUuid") != null)
                    .map(r -> {
                        CaseRelationship rel = new CaseRelationship();
                        rel.setFromCaseUuid((String) r.get("fromCaseUuid"));
                        rel.setToCaseUuid((String) r.get("toCaseUuid"));
                        return rel;
                    })
                    .collect(Collectors.toList());
            dto.setRelationships(relationships);
        }

        return dto;
    }
}
