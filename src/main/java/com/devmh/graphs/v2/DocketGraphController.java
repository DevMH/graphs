package com.devmh.graphs.v2;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dockets/{docketUuid}/graph")
public class DocketGraphController {

    private final DocketGraphService docketGraphService;

    public DocketGraphController(DocketGraphService docketGraphService) {
        this.docketGraphService = docketGraphService;
    }

    /**
     * Retrieves the complete graph for a specific docket version
     * GET /api/dockets/{docketUuid}/graph/versions/{versionNumber}
     */
    @GetMapping("/versions/{versionNumber}")
    public ResponseEntity<DocketGraphDTO> getDocketVersionGraph(
            @PathVariable String docketUuid,
            @PathVariable Integer versionNumber) {

        DocketGraphDTO graph = docketGraphService.getDocketVersionGraph(docketUuid, versionNumber);
        return ResponseEntity.ok(graph);
    }

    /**
     * Creates a new version by replacing the entire graph
     * POST /api/dockets/{docketUuid}/graph
     * Body: { "description": "...", "cases": [...], "relationships": [...] }
     */
    @PostMapping
    public ResponseEntity<GraphSyncResult> replaceGraph(
            @PathVariable String docketUuid,
            @RequestBody ReplaceGraphRequest request) {

        GraphSyncResult result = docketGraphService.replaceGraph(docketUuid, request);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    /**
     * Updates an existing version's graph (synchronizes with application model)
     * PUT /api/dockets/{docketUuid}/graph/versions/{versionNumber}
     * Body: { "cases": [...], "relationships": [...] }
     */
    @PutMapping("/versions/{versionNumber}")
    public ResponseEntity<GraphSyncResult> updateVersionGraph(
            @PathVariable String docketUuid,
            @PathVariable Integer versionNumber,
            @RequestBody ReplaceGraphRequest request) {

        GraphSyncResult result = docketGraphService.updateVersionGraph(
                docketUuid, versionNumber, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Get statistics about a version
     * GET /api/dockets/{docketUuid}/graph/versions/{versionNumber}/statistics
     */
    @GetMapping("/versions/{versionNumber}/statistics")
    public ResponseEntity<Map<String, Object>> getVersionStatistics(
            @PathVariable String docketUuid,
            @PathVariable Integer versionNumber) {

        Map<String, Object> stats = docketGraphService.getVersionStatistics(docketUuid, versionNumber);
        return ResponseEntity.ok(stats);
    }

    /**
     * Compare two versions
     * GET /api/dockets/{docketUuid}/graph/compare?version1=1&version2=2
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareVersions(
            @PathVariable String docketUuid,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {

        Map<String, Object> comparison = docketGraphService.compareVersions(
                docketUuid, version1, version2);
        return ResponseEntity.ok(comparison);
    }
}
