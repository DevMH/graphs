package com.devmh.graphs.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
public class DocketGraphService {

    private static final Logger logger = LoggerFactory.getLogger(DocketGraphService.class);

    private final DocketRepository docketRepository;
    private final DocketVersionRepository docketVersionRepository;
    private final DocketGraphRepository docketGraphRepository;
    private final GraphSyncService graphSyncService;

    public DocketGraphService(
            DocketRepository docketRepository,
            DocketVersionRepository docketVersionRepository,
            DocketGraphRepository docketGraphRepository,
            GraphSyncService graphSyncService) {
        this.docketRepository = docketRepository;
        this.docketVersionRepository = docketVersionRepository;
        this.docketGraphRepository = docketGraphRepository;
        this.graphSyncService = graphSyncService;
    }

    /**
     * Retrieves the complete graph for a specific docket version
     * This is optimized for read performance using a single Cypher query
     */
    @Transactional(readOnly = true)
    public DocketGraphDTO getDocketVersionGraph(String docketUuid, Integer versionNumber) {
        logger.info("Retrieving graph for docket {} version {}", docketUuid, versionNumber);

        // Verify docket exists
        if (!docketRepository.existsById(docketUuid)) {
            throw new ResourceNotFoundException("Docket not found with uuid: " + docketUuid);
        }

        // Verify version exists
        docketVersionRepository.findByDocketUuidAndVersionNumber(docketUuid, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + versionNumber + " not found for docket " + docketUuid));

        long startTime = System.currentTimeMillis();
        DocketGraphDTO graph = docketGraphRepository.getDocketVersionGraph(docketUuid, versionNumber);
        long duration = System.currentTimeMillis() - startTime;

        logger.info("Retrieved graph with {} cases and {} relationships in {}ms",
                graph.getCases().size(),
                graph.getRelationships().size(),
                duration);

        return graph;
    }

    /**
     * Replaces the entire graph for a docket version using efficient synchronization
     * This creates a new version and syncs the application model with Neo4j
     */
    public GraphSyncResult replaceGraph(String docketUuid, ReplaceGraphRequest request) {
        logger.info("Replacing graph for docket {}", docketUuid);

        long startTime = System.currentTimeMillis();

        // Verify docket exists
        if (!docketRepository.existsById(docketUuid)) {
            throw new ResourceNotFoundException("Docket not found with uuid: " + docketUuid);
        }

        // Get next version number
        Integer maxVersion = docketVersionRepository.findMaxVersionNumber(docketUuid).orElse(0);
        Integer newVersionNumber = maxVersion + 1;

        // Use efficient sync service to replace the graph
        GraphSyncResult result = graphSyncService.syncGraph(
                docketUuid,
                newVersionNumber,
                request.getCases(),
                request.getRelationships()
        );

        result.setVersionNumber(newVersionNumber);
        result.setDurationMs(System.currentTimeMillis() - startTime);

        logger.info("Graph replaced for docket {} version {} in {}ms: {} cases (+{} -{} ~{}), {} relationships (+{} -{})",
                docketUuid, newVersionNumber, result.getDurationMs(),
                request.getCases().size(), result.getCasesAdded(), result.getCasesRemoved(), result.getCasesUpdated(),
                request.getRelationships().size(), result.getRelationshipsAdded(), result.getRelationshipsRemoved());

        return result;
    }

    /**
     * Updates an existing version's graph (synchronizes application model with Neo4j)
     */
    public GraphSyncResult updateVersionGraph(
            String docketUuid,
            Integer versionNumber,
            ReplaceGraphRequest request) {

        logger.info("Updating graph for docket {} version {}", docketUuid, versionNumber);

        long startTime = System.currentTimeMillis();

        // Verify version exists
        docketVersionRepository.findByDocketUuidAndVersionNumber(docketUuid, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + versionNumber + " not found for docket " + docketUuid));

        // Use efficient sync service
        GraphSyncResult result = graphSyncService.syncGraph(
                docketUuid,
                versionNumber,
                request.getCases(),
                request.getRelationships()
        );

        result.setVersionNumber(versionNumber);
        result.setDurationMs(System.currentTimeMillis() - startTime);

        logger.info("Graph updated for docket {} version {} in {}ms",
                docketUuid, versionNumber, result.getDurationMs());

        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getVersionStatistics(String docketUuid, Integer versionNumber) {
        // Verify version exists
        docketVersionRepository.findByDocketUuidAndVersionNumber(docketUuid, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + versionNumber + " not found for docket " + docketUuid));

        return docketGraphRepository.getDocketVersionStatistics(docketUuid, versionNumber);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(
            String docketUuid,
            Integer version1,
            Integer version2) {

        // Verify both versions exist
        docketVersionRepository.findByDocketUuidAndVersionNumber(docketUuid, version1)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + version1 + " not found for docket " + docketUuid));

        docketVersionRepository.findByDocketUuidAndVersionNumber(docketUuid, version2)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + version2 + " not found for docket " + docketUuid));

        return docketGraphRepository.compareDocketVersions(docketUuid, version1, version2);
    }
}
