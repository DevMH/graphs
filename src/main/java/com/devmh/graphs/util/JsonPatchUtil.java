package com.devmh.graphs.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;

import java.util.LinkedHashMap;
import java.util.Map;

public class JsonPatchUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static <T> T apply(JsonNode patchNode, T target, Class<T> type) {
        try {
            JsonPatch patch = JsonPatch.fromJson(patchNode);
            JsonNode targetNode = MAPPER.valueToTree(target);
            JsonNode patched = patch.apply(targetNode);
            return MAPPER.treeToValue(patched, type);
        } catch (JsonPatchException | JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON Patch: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> applyToMap(JsonNode patchNode, Map<String,Object> current) {
        try {
            if (current == null) current = Map.of();
            JsonPatch patch = JsonPatch.fromJson(patchNode);
            JsonNode targetNode = MAPPER.valueToTree(current);
            JsonNode patched = patch.apply(targetNode);
            return MAPPER.treeToValue(patched, LinkedHashMap.class);
        } catch (JsonPatchException | JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON Patch for map: " + e.getMessage(), e);
        }
    }
}
