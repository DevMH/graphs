package com.devmh.graphs.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseGraphController {

    private final ObjectMapper om;
    private final CaseVersionGraphRepository graphRepo;
    private final CaseVersionGraphPatchService patchService;

    @GetMapping("/{caseId}/versions/{versionId}/team-graph")
    public CaseVersionGraph getTeamGraph(
            @PathVariable String caseId,
            @PathVariable String versionId) {
        return graphRepo.loadGraph(caseId, versionId);
    }

    @PatchMapping(
            path = "/{caseId}/versions/{versionId}/team-graph",
            consumes = "application/json-patch+json",
            produces = "application/json"
    )
    public ResponseEntity<CaseVersionGraph> patchTeamGraph(
            @PathVariable String caseId,
            @PathVariable String versionId,
            @RequestBody JsonNode patchBody
    ) {
        // 1) Load current graph
        var current = graphRepo.loadGraph(caseId, versionId);
        var currentNode = om.valueToTree(current);

        // 2) Apply RFC-6902 patch (fge)
        final JsonPatch jsonPatch;
        try {
            jsonPatch = JsonPatch.fromJson(patchBody);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        final JsonNode patchedNode;
        try {
            patchedNode = jsonPatch.apply(currentNode);
        } catch (Exception e) {
            return ResponseEntity.unprocessableEntity().build();
        }

        // 3) Deserialize to DTO
        final CaseVersionGraph patched;
        try {
            patched = om.treeToValue(patchedNode, CaseVersionGraph.class);
        } catch (Exception e) {
            return ResponseEntity.status(415).build();
        }

        // 4) Validate allowed shape (defensive)
        if (!isValidGraph(patched)) {
            return ResponseEntity.badRequest().build();
        }

        // 5) Compute delta
        var delta = GraphDiffer.diff(current, patched);

        // 6) Persist delta (transactional)
        patchService.applyDelta(caseId, versionId, delta);

        // 7) Return the new projection (optional: reload to reflect generated IDs)
        var reloaded = graphRepo.loadGraph(caseId, versionId);
        return ResponseEntity.ok(reloaded);
    }

    private boolean isValidGraph(CaseVersionGraph g) {
        if (g.teamIds() == null || g.edges() == null) return false;
        for (var e : g.edges()) {
            if (e == null) return false;
            if (e.from() == null || e.from().isBlank()) return false;
            if (e.to() == null || e.to().isBlank()) return false;
            if (e.kind() == null || e.kind().isBlank()) return false;
        }
        return true;
    }

}
