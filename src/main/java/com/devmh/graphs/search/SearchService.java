package com.devmh.graphs.search;

import lombok.Value;
import org.neo4j.driver.types.Node;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    private final Neo4jClient neo4j;


    /** Execute arbitrary Cypher and return rows as maps */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> cypher(String query, Map<String, Object> params) {
        log.debug("Cypher search: {} params={}", query, params);
        return new ArrayList<>(neo4j.query(query).bindAll(params == null ? Map.of() : params).fetch().all());
    }

    /** Fulltext (Lucene) query over nodes using a configured index. */
    @Transactional(readOnly = true)
    public List<LuceneNode> luceneNodes(String indexName, String q, Integer limit) {
        int l = (limit == null || limit <= 0) ? 25 : Math.min(limit, 500);
        String cypher = "CALL db.index.fulltext.queryNodes($index,$q) YIELD node, score " +
                "RETURN node, score ORDER BY score DESC LIMIT $limit";
        return neo4j.query(cypher)
                .bind(indexName).to("index")
                .bind(q).to("q")
                .bind(l).to("limit")
                .fetch()
                .all()
                .stream()
                .map(row -> new LuceneNode(simpleNode((Node) row.get("node")), ((Number) row.get("score")).doubleValue()))
                .toList();
    }

    private Map<String,Object> simpleNode(Node n) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("labels", n.labels());
        Map<String,Object> props = new LinkedHashMap<>(n.asMap());
// Promote string id property if present
        m.put("id", props.getOrDefault("id", null));
        m.put("props", props);
        return m;
    }

    @Value
    public static class LuceneNode {
        Map<String,Object> node;
        double score;
    }
}
