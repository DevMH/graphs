package com.devmh.graphs.generic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericGraphService {
    private final GenericNodeRepository nodeRepository;
    private final Neo4jClient neo4j;

    /** Create or update a generic node using SDN (labels + composite props supported). */
    @Transactional
    public GenericNode saveNode(GenericNode node) {
        log.debug("Saving GenericNode: labels={}, props={}", node.getLabels(), node.getProps());
        return nodeRepository.save(node);
    }

    /** Fetch a node by id (generic). */
    @Transactional(readOnly = true)
    public Optional<GenericNode> findNode(String id) {
        return nodeRepository.findById(id);
    }

    /**
     * Create or update a relationship between two generic nodes with a dynamic type and arbitrary properties.
     * This uses Neo4jClient because SDN requires compile-time relationship types.
     */
    @Transactional
    public void relate(String fromNodeId, String relationshipType, String toNodeId, Map<String, Object> relProps) {
        if (relationshipType == null || relationshipType.isBlank()) {
            throw new IllegalArgumentException("relationshipType must be provided");
        }
        String safeType = backtick(relationshipType);
        Map<String, Object> params = new HashMap<>();
        params.put("fromId", fromNodeId);
        params.put("toId", toNodeId);
        params.put("props", relProps == null ? Map.of() : relProps);

        String cypher = """
                        MATCH (a {id:$fromId}), (b {id:$toId})
                        MERGE (a)-[r:%s]->(b)
                        SET r += $props
                        RETURN type(r) AS type
                        """.formatted(safeType);
        log.debug("Relate Cypher: {} params={} ", cypher, params);
        neo4j.query(cypher).bindAll(params).run();
    }

    /** Persist an in-memory generic graph (nodes + relationships) in order. */
    @Transactional
    public List<GenericNode> saveGraph(GenericGraph graph) {
        List<GenericNode> saved = new ArrayList<>();
        for (GenericNode n : graph.getNodes()) {
            saved.add(saveNode(n));
        }
        for (GenericRelationship r : graph.getRelationships()) {
            String fromId = saved.get(r.getFromIndex()).getId();
            String toId = saved.get(r.getToIndex()).getId();
            relate(fromId, r.getType(), toId, r.getProps());
        }
        return saved;
    }

    /** Helper to backtick-escape a label or type. */
    private static String backtick(String s) {
        String cleaned = s.replace("`", "");
        return "`" + cleaned + "`";
    }
}
