package com.devmh.graphs.typed;

import com.devmh.graphs.util.JsonPatchUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/typed-ops")
@RequiredArgsConstructor
@Validated
public class TypedOpController {
    private final TypedOpsService service;

    @PatchMapping(value = "/graphs/case/{id}", consumes = "application/json-patch+json")
    public Case patchCaseGraph(@PathVariable String id, @RequestBody JsonNode patch) {
        Case current = service.getCaseGraph(id);
        Case patched = JsonPatchUtil.apply(patch, current, Case.class);
        return service.patchCaseGraph(patched);
    }

    @PatchMapping(value = "/graphs/docket/{id}", consumes = "application/json-patch+json")
    public Docket patchDocketGraph(@PathVariable String id, @RequestBody JsonNode patch) {
        Docket current = service.getDocketGraph(id);
        Docket patched = JsonPatchUtil.apply(patch, current, Docket.class);
        return service.patchDocketGraph(patched);
    }

    @PatchMapping(value = "/edges/{type}", consumes = "application/json-patch+json")
    public TypedOpsService.EdgeDiff patchEdgePropertiesJsonPatch(
            @PathVariable String type,
            @RequestParam @NotBlank String fromId,
            @RequestParam @NotBlank String toId,
            @RequestBody JsonNode patch
    ) {
        var current = service.getEdgeProps(fromId, type, toId);
        var desired = JsonPatchUtil.applyToMap(patch, current);
        return service.upsertEdgeWithDiff(fromId, type, toId, desired, false);
    }
}
