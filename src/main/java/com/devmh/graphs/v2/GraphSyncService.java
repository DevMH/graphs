package com.devmh.graphs.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Efficiently synchronizes application graph model with Neo4j database
 * Optimized for performance through:
 * 1. Single-pass diff calculation using hash sets
 * 2. Batched UNWIND operations for bulk creates/updates
 * 3. Efficient MERGE operations to avoid duplicate work
 * 4. Minimized database round trips
 */
@Service
public class GraphSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GraphSyncService.class);
    private static final int BATCH_SIZE = 1000;

    private final Neo4jClient neo4jClient;

    public GraphSyncService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * Synchronizes the application model graph with Neo4j for a specific docket version
     * Uses efficient diff-based approach to minimize database operations
     */
    @Transactional
    public GraphSyncResult syncGraph(
            String docketUuid,
            Integer versionNumber,
            List<CaseNode> applicationCases,
            List<CaseRelationship> applicationRelationships) {

        long startTime = System.currentTimeMillis();
        GraphSyncResult result = new GraphSyncResult();

        logger.debug("Starting graph sync for docket {} version {}", docketUuid, versionNumber);

        // Step 1: Ensure docket version exists
        ensureDocketVersionExists(docketUuid, versionNumber);

        // Step 2: Get current state from Neo4j (single query for efficiency)
        GraphState currentState = getCurrentGraphState(docketUuid, versionNumber);
        logger.debug("Current state: {} cases, {} relationships",
                currentState.caseUuids.size(), currentState.relationships.size());

        // Step 3: Calculate diff (in-memory, very fast)
        GraphDiff diff = calculateDiff(currentState, applicationCases, applicationRelationships);
        logger.debug("Diff calculated: +{} -{} ~{} cases, +{} -{} relationships",
                diff.casesToAdd.size(), diff.casesToRemove.size(), diff.casesToUpdate.size(),
                diff.relationshipsToAdd.size(), diff.relationshipsToRemove.size());

        // Step 4: Apply changes efficiently using batched operations
        applyCaseChanges(docketUuid, versionNumber, diff);
        applyRelationshipChanges(docketUuid, versionNumber, diff);

        // Step 5: Populate result
        result.setCasesAdded(diff.casesToAdd.size());
        result.setCasesRemoved(diff.casesToRemove.size());
        result.setCasesUpdated(diff.casesToUpdate.size());
        result.setRelationshipsAdded(diff.relationshipsToAdd.size());
        result.setRelationshipsRemoved(diff.relationshipsToRemove.size());
        result.setDurationMs(System.currentTimeMillis() - startTime);

        logger.info("Graph sync completed in {}ms", result.getDurationMs());

        return result;
    }

    /**
     * Ensures the DocketVersion node exists
     */
    private void ensureDocketVersionExists(String docketUuid, Integer versionNumber) {
        String query = """
            MATCH (d:Docket {uuid: $docketUuid})
            MERGE (d)-[:HAS_VERSION]->(dv:DocketVersion {versionNumber: $versionNumber})
            ON CREATE SET dv.uuid = randomUUID(),
                         dv.createdAt = datetime(),
                         dv.isActive = true,
                         dv.description = 'Version ' + $versionNumber
            RETURN dv.uuid as versionUuid
            """;

        neo4jClient.query(query)
                .bind(docketUuid).to("docketUuid")
                .bind(versionNumber).to("versionNumber")
                .run();
    }

    /**
     * Retrieves current graph state in a single optimized query
     */
    private GraphState getCurrentGraphState(String docketUuid, Integer versionNumber) {
        String query = """
            MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion {versionNumber: $versionNumber})
            OPTIONAL MATCH (dv)-[:CONTAINS_CASE]->(cs:CaseSnapshot)-[:SNAPSHOT_OF]->(c:Case)
            OPTIONAL MATCH (cs)-[r:RELATED_TO_CASE]->(relatedCs:CaseSnapshot)-[:SNAPSHOT_OF]->(relatedC:Case)
            RETURN collect(DISTINCT {caseUuid: c.uuid, caseName: c.name, snapshotUuid: cs.uuid}) as cases,
                   collect(DISTINCT {fromCaseUuid: c.uuid, toCaseUuid: relatedC.uuid}) as relationships
            """;

        Map<String, Object> result = neo4jClient.query(query)
                .bind(docketUuid).to("docketUuid")
                .bind(versionNumber).to("versionNumber")
                .fetch()
                .one()
                .orElse(Map.of("cases", List.of(), "relationships", List.of()));

        return parseGraphState(result);
    }

    private GraphState parseGraphState(Map<String, Object> result) {
        GraphState state = new GraphState();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> casesRaw = (List<Map<String, Object>>) result.get("cases");
        if (casesRaw != null) {
            for (Map<String, Object> caseData : casesRaw) {
                String caseUuid = (String) caseData.get("caseUuid");
                if (caseUuid != null) {
                    state.caseUuids.add(caseUuid);
                    state.caseNames.put(caseUuid, (String) caseData.get("caseName"));
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relsRaw = (List<Map<String, Object>>) result.get("relationships");
        if (relsRaw != null) {
            for (Map<String, Object> relData : relsRaw) {
                String fromUuid = (String) relData.get("fromCaseUuid");
                String toUuid = (String) relData.get("toCaseUuid");
                if (fromUuid != null && toUuid != null) {
                    state.relationships.add(new RelationshipKey(fromUuid, toUuid));
                }
            }
        }

        return state;
    }

    /**
     * Calculates the diff between current and desired state
     * This is done entirely in memory for maximum performance
     */
    private GraphDiff calculateDiff(
            GraphState currentState,
            List<CaseNode> applicationCases,
            List<CaseRelationship> applicationRelationships) {

        GraphDiff diff = new GraphDiff();

        // Build sets for O(1) lookups
        Set<String> appCaseUuids = applicationCases.stream()
                .map(CaseNode::getUuid)
                .collect(Collectors.toSet());

        Map<String, String> appCaseNames = applicationCases.stream()
                .collect(Collectors.toMap(CaseNode::getUuid, CaseNode::getName));

        Set<RelationshipKey> appRelationships = applicationRelationships.stream()
                .map(r -> new RelationshipKey(r.getFromCaseUuid(), r.getToCaseUuid()))
                .collect(Collectors.toSet());

        // Calculate case diff
        for (String appCaseUuid : appCaseUuids) {
            if (!currentState.caseUuids.contains(appCaseUuid)) {
                // Case exists in app but not in Neo4j - add it
                diff.casesToAdd.add(new CaseNode(appCaseUuid, appCaseNames.get(appCaseUuid)));
            } else {
                // Case exists in both - check if name changed
                String currentName = currentState.caseNames.get(appCaseUuid);
                String appName = appCaseNames.get(appCaseUuid);
                if (!Objects.equals(currentName, appName)) {
                    diff.casesToUpdate.add(new CaseNode(appCaseUuid, appName));
                }
            }
        }

        for (String currentCaseUuid : currentState.caseUuids) {
            if (!appCaseUuids.contains(currentCaseUuid)) {
                // Case exists in Neo4j but not in app - remove it
                diff.casesToRemove.add(currentCaseUuid);
            }
        }

        // Calculate relationship diff
        for (RelationshipKey appRel : appRelationships) {
            if (!currentState.relationships.contains(appRel)) {
                diff.relationshipsToAdd.add(appRel);
            }
        }

        for (RelationshipKey currentRel : currentState.relationships) {
            if (!appRelationships.contains(currentRel)) {
                diff.relationshipsToRemove.add(currentRel);
            }
        }

        return diff;
    }

    /**
     * Applies case changes efficiently using batched operations
     */
    private void applyCaseChanges(String docketUuid, Integer versionNumber, GraphDiff diff) {
        // Remove cases (snapshots only, keep underlying Case nodes)
        if (!diff.casesToRemove.isEmpty()) {
            removeCaseSnapshots(docketUuid, versionNumber, diff.casesToRemove);
        }

        // Add new cases (ensure Case nodes exist, create snapshots)
        if (!diff.casesToAdd.isEmpty()) {
            addCases(docketUuid, versionNumber, diff.casesToAdd);
        }

        // Update existing cases
        if (!diff.casesToUpdate.isEmpty()) {
            updateCases(diff.casesToUpdate);
        }
    }

    /**
     * Removes case snapshots for a version (batched)
     */
    private void removeCaseSnapshots(String docketUuid, Integer versionNumber, Set<String> caseUuids) {
        List<List<String>> batches = partition(new ArrayList<>(caseUuids), BATCH_SIZE);

        for (List<String> batch : batches) {
            String query = """
                MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion {versionNumber: $versionNumber})
                MATCH (dv)-[:CONTAINS_CASE]->(cs:CaseSnapshot)-[:SNAPSHOT_OF]->(c:Case)
                WHERE c.uuid IN $caseUuids
                DETACH DELETE cs
                """;

            neo4jClient.query(query)
                    .bind(docketUuid).to("docketUuid")
                    .bind(versionNumber).to("versionNumber")
                    .bind(batch).to("caseUuids")
                    .run();
        }
    }

    /**
     * Adds cases efficiently using UNWIND for batch processing
     * Uses MERGE to avoid creating duplicate Case nodes
     */
    private void addCases(String docketUuid, Integer versionNumber, Set<CaseNode> cases) {
        List<List<CaseNode>> batches = partition(new ArrayList<>(cases), BATCH_SIZE);

        for (List<CaseNode> batch : batches) {
            // First ensure all Case nodes exist
            String ensureCasesQuery = """
                UNWIND $cases as caseData
                MERGE (c:Case {uuid: caseData.uuid})
                ON CREATE SET c.name = caseData.name,
                             c.createdAt = datetime(),
                             c.updatedAt = datetime()
                ON MATCH SET c.name = caseData.name,
                            c.updatedAt = datetime()
                """;

            List<Map<String, Object>> caseData = batch.stream()
                    .map(c -> Map.<String, Object>of("uuid", c.getUuid(), "name", c.getName()))
                    .collect(Collectors.toList());

            neo4jClient.query(ensureCasesQuery)
                    .bind(caseData).to("cases")
                    .run();

            // Then create snapshots and link them
            String createSnapshotsQuery = """
                MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion {versionNumber: $versionNumber})
                UNWIND $cases as caseData
                MATCH (c:Case {uuid: caseData.uuid})
                CREATE (cs:CaseSnapshot {
                    uuid: randomUUID(),
                    snapshotAt: datetime()
                })
                CREATE (dv)-[:CONTAINS_CASE]->(cs)
                CREATE (cs)-[:SNAPSHOT_OF]->(c)
                """;

            neo4jClient.query(createSnapshotsQuery)
                    .bind(docketUuid).to("docketUuid")
                    .bind(versionNumber).to("versionNumber")
                    .bind(caseData).to("cases")
                    .run();
        }
    }

    /**
     * Updates case names efficiently using UNWIND
     */
    private void updateCases(Set<CaseNode> cases) {
        List<List<CaseNode>> batches = partition(new ArrayList<>(cases), BATCH_SIZE);

        for (List<CaseNode> batch : batches) {
            String query = """
                UNWIND $cases as caseData
                MATCH (c:Case {uuid: caseData.uuid})
                SET c.name = caseData.name,
                    c.updatedAt = datetime()
                """;

            List<Map<String, Object>> caseData = batch.stream()
                    .map(c -> Map.<String, Object>of("uuid", c.getUuid(), "name", c.getName()))
                    .collect(Collectors.toList());

            neo4jClient.query(query)
                    .bind(caseData).to("cases")
                    .run();
        }
    }

    /**
     * Applies relationship changes efficiently
     */
    private void applyRelationshipChanges(String docketUuid, Integer versionNumber, GraphDiff diff) {
        // Remove relationships
        if (!diff.relationshipsToRemove.isEmpty()) {
            removeRelationships(docketUuid, versionNumber, diff.relationshipsToRemove);
        }

        // Add relationships
        if (!diff.relationshipsToAdd.isEmpty()) {
            addRelationships(docketUuid, versionNumber, diff.relationshipsToAdd);
        }
    }

    /**
     * Removes relationships efficiently using UNWIND
     */
    private void removeRelationships(String docketUuid, Integer versionNumber, Set<RelationshipKey> relationships) {
        List<List<RelationshipKey>> batches = partition(new ArrayList<>(relationships), BATCH_SIZE);

        for (List<RelationshipKey> batch : batches) {
            String query = """
                MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion {versionNumber: $versionNumber})
                UNWIND $relationships as rel
                MATCH (dv)-[:CONTAINS_CASE]->(fromCs:CaseSnapshot)-[:SNAPSHOT_OF]->(fromC:Case {uuid: rel.fromUuid})
                MATCH (dv)-[:CONTAINS_CASE]->(toCs:CaseSnapshot)-[:SNAPSHOT_OF]->(toC:Case {uuid: rel.toUuid})
                MATCH (fromCs)-[r:RELATED_TO_CASE]->(toCs)
                DELETE r
                """;

            List<Map<String, Object>> relData = batch.stream()
                    .map(r -> Map.<String, Object>of("fromUuid", r.fromUuid, "toUuid", r.toUuid))
                    .collect(Collectors.toList());

            neo4jClient.query(query)
                    .bind(docketUuid).to("docketUuid")
                    .bind(versionNumber).to("versionNumber")
                    .bind(relData).to("relationships")
                    .run();
        }
    }

    /**
     * Adds relationships efficiently using UNWIND and MERGE
     */
    private void addRelationships(String docketUuid, Integer versionNumber, Set<RelationshipKey> relationships) {
        List<List<RelationshipKey>> batches = partition(new ArrayList<>(relationships), BATCH_SIZE);

        for (List<RelationshipKey> batch : batches) {
            String query = """
                MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion {versionNumber: $versionNumber})
                UNWIND $relationships as rel
                MATCH (dv)-[:CONTAINS_CASE]->(fromCs:CaseSnapshot)-[:SNAPSHOT_OF]->(fromC:Case {uuid: rel.fromUuid})
                MATCH (dv)-[:CONTAINS_CASE]->(toCs:CaseSnapshot)-[:SNAPSHOT_OF]->(toC:Case {uuid: rel.toUuid})
                MERGE (fromCs)-[:RELATED_TO_CASE]->(toCs)
                """;

            List<Map<String, Object>> relData = batch.stream()
                    .map(r -> Map.<String, Object>of("fromUuid", r.fromUuid, "toUuid", r.toUuid))
                    .collect(Collectors.toList());

            neo4jClient.query(query)
                    .bind(docketUuid).to("docketUuid")
                    .bind(versionNumber).to("versionNumber")
                    .bind(relData).to("relationships")
                    .run();
        }
    }

    /**
     * Partitions a list into batches for efficient processing
     */
    private <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches.isEmpty() ? List.of(List.of()) : batches;
    }

    // ============================================
    // INTERNAL DATA STRUCTURES
    // ============================================

    /**
     * Represents the current state of the graph in Neo4j
     */
    private static class GraphState {
        Set<String> caseUuids = new HashSet<>();
        Map<String, String> caseNames = new HashMap<>();
        Set<RelationshipKey> relationships = new HashSet<>();
    }

    /**
     * Represents the differences between current and desired state
     */
    private static class GraphDiff {
        Set<CaseNode> casesToAdd = new HashSet<>();
        Set<String> casesToRemove = new HashSet<>();
        Set<CaseNode> casesToUpdate = new HashSet<>();
        Set<RelationshipKey> relationshipsToAdd = new HashSet<>();
        Set<RelationshipKey> relationshipsToRemove = new HashSet<>();
    }

    /**
     * Efficient key for relationship comparison
     */
    private static class RelationshipKey {
        final String fromUuid;
        final String toUuid;

        RelationshipKey(String fromUuid, String toUuid) {
            this.fromUuid = fromUuid;
            this.toUuid = toUuid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelationshipKey that = (RelationshipKey) o;
            return Objects.equals(fromUuid, that.fromUuid) && Objects.equals(toUuid, that.toUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fromUuid, toUuid);
        }
    }
}
