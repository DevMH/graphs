package com.devmh.graphs.typed;

import com.devmh.graphs.generic.GenericGraph;
import com.devmh.graphs.generic.GenericGraphService;
import com.devmh.graphs.generic.GenericNode;
import com.devmh.graphs.generic.GenericRelationship;
import com.devmh.graphs.mapper.GraphMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TypedOpsService {
    private final GenericGraphService genericService;
    private final GraphMapper mapper;
    private final Neo4jClient neo4j;

    // ===================== NODES =====================
    @Transactional
    public <T> T upsertNode(T typed) {
        GenericNode gn = switch (typed) {
            case Case c -> mapper.fromTypedCase(c).getNodes().getFirst();
            case Docket d -> mapper.fromTypedDocket(d).getNodes().getFirst();
            case Judge j -> mapPersonToGeneric(j);
            case Lawyer l -> mapPersonToGeneric(l);
            case Person p -> mapPersonToGeneric(p);
            default -> throw new IllegalArgumentException("Unsupported node type: " + typed.getClass());
        };
        GenericNode saved = genericService.saveNode(gn);
        // propagate generated id back
        switch (typed) {
            case Case c -> c.setId(saved.getId());
            case Docket d -> d.setId(saved.getId());
            case Person p -> p.setId(saved.getId());
            default -> { }
        }
        return typed;
    }

    @Transactional
    public void deleteNode(String nodeId) {
        neo4j.query("MATCH (n {id:$id}) DETACH DELETE n")
                .bind(nodeId).to("id")
                .run();
    }

    // ===================== EDGES =====================
    @Transactional
    public void putEdge(String fromId, String type, String toId, Map<String,Object> props) {
        genericService.relate(fromId, type, toId, props);
    }

    @Transactional
    public void deleteEdge(String fromId, String type, String toId) {
        String cypher = "MATCH (a {id:$from}),(b {id:$to}) MATCH (a)-[r:" + escapeType(type) + "]->(b) DELETE r";
        neo4j.query(cypher)
                .bind(fromId).to("from")
                .bind(toId).to("to")
                .run();
    }

    @Transactional(readOnly = true)
    public Map<String,Object> getEdgeProps(String fromId, String type, String toId) {
        String cypher = "MATCH (a {id:$from})-[r:%s]->(b {id:$to}) RETURN properties(r) AS props".formatted(escapeType(type));
        return neo4j.query(cypher)
                .bind(fromId).to("from")
                .bind(toId).to("to")
                .fetchAs(Map.class)
                .one()
                .orElse(Map.of());
    }

    @Transactional
    public EdgeDiff upsertEdgeWithDiff(String fromId, String type, String toId, Map<String,Object> desiredProps, boolean replace) {
        Map<String,Object> current = getEdgeProps(fromId, type, toId);
        Map<String,Object> desired = desiredProps == null ? Map.of() : desiredProps;

        Map<String,Object> toSet = new LinkedHashMap<>();
        List<String> toRemove = new ArrayList<>();
        Map<String, Change> updated = new LinkedHashMap<>();
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();

        if (replace) {
            for (String k : current.keySet()) {
                if (!desired.containsKey(k)) { toRemove.add(k); removed.add(k); }
            }
        }
        for (Map.Entry<String,Object> e : desired.entrySet()) {
            String k = e.getKey(); Object v = e.getValue();
            if (v == null) {
                if (current.containsKey(k)) { toRemove.add(k); removed.add(k); }
            } else if (!current.containsKey(k)) {
                toSet.put(k, v); added.add(k);
            } else if (!Objects.equals(current.get(k), v)) {
                toSet.put(k, v); updated.put(k, new Change(current.get(k), v));
            }
        }

        String cypher = """
            MATCH (a {id:$from}),(b {id:$to})
            MERGE (a)-[r:%s]->(b)
            WITH r, $toRemove AS rem, $toSet AS sp
            UNWIND rem AS k SET r[k] = null
            WITH r, sp
            SET r += sp
            RETURN properties(r) AS props
            """.formatted(escapeType(type));

        Map<String,Object> finalProps = neo4j.query(cypher)
                .bind(fromId).to("from")
                .bind(toId).to("to")
                .bind(toRemove).to("toRemove")
                .bind(toSet).to("toSet")
                .fetchAs(Map.class)
                .one()
                .orElse(Map.of());


        return EdgeDiff.builder()
                .fromId(fromId).toId(toId).type(type)
                .added(added).removed(removed).updated(updated)
                .finalProps(finalProps)
                .build();
    }

    @Data @AllArgsConstructor
    public static class Change { Object oldVal; Object newVal; }

    @Data @Builder
    public static class EdgeDiff {
        private String fromId; private String toId; private String type;
        private List<String> added; private List<String> removed; private Map<String,Change> updated;
        private Map<String,Object> finalProps;
    }

    @Transactional
    public Case postCaseGraph(Case c) {
        GenericGraph gg = mapper.fromTypedCase(c);
        List<GenericNode> saved = genericService.saveGraph(gg);
        return mapper.toTypedCase(projectIdsBack(gg, saved));
    }

    @Transactional
    public Docket postDocketGraph(Docket d) {
        GenericGraph gg = mapper.fromTypedDocket(d);
        List<GenericNode> saved = genericService.saveGraph(gg);
        return mapper.toTypedDocket(projectIdsBack(gg, saved));
    }

    @Transactional
    public Case patchCaseGraph(Case c) {
        GenericGraph gg = mapper.fromTypedCase(c);
        for (GenericNode n : gg.getNodes()) mergeNode(n);
        gg.getRelationships().forEach(r -> {
            String fromId = gg.getNodes().get(r.getFromIndex()).getId();
            String toId = gg.getNodes().get(r.getToIndex()).getId();
            genericService.relate(fromId, r.getType(), toId, r.getProps());
        });
        return c;
    }

    @Transactional
    public Docket patchDocketGraph(Docket d) {
        GenericGraph gg = mapper.fromTypedDocket(d);
        for (GenericNode n : gg.getNodes()) mergeNode(n);
        gg.getRelationships().forEach(r -> {
            String fromId = gg.getNodes().get(r.getFromIndex()).getId();
            String toId = gg.getNodes().get(r.getToIndex()).getId();
            genericService.relate(fromId, r.getType(), toId, r.getProps());
        });
        return d;
    }

    @Transactional(readOnly = true)
    public Case getCaseGraph(String caseId) {
        GenericGraph g = exportCaseGeneric(caseId);
        return mapper.toTypedCase(g);
    }

    @Transactional(readOnly = true)
    public Docket getDocketGraph(String docketId) {
        GenericGraph g = exportDocketGeneric(docketId);
        return mapper.toTypedDocket(g);
    }

    private GenericGraph exportCaseGeneric(String caseId) {
        List<GenericNode> nodes = new ArrayList<>();
        List<GenericRelationship> rels = new ArrayList<>();
        Map<String,Integer> indexOf = new LinkedHashMap<>();

        var row = neo4j.query("""
                MATCH (c:Case {id:$id})
                OPTIONAL MATCH (c)-[r:ASSIGNED_TO|REVIEWS]->(p)
                RETURN c AS c, collect(DISTINCT r) AS rels, collect(DISTINCT p) AS persons
                """)
                .bind(caseId).to("id")
                .fetch().one().orElse(Map.of());

        if (row.isEmpty()) {
            return GenericGraph.builder().build();
        }

        Node c = (Node) row.get("c");
        @SuppressWarnings("unchecked") List<Relationship> rs = (List<Relationship>) row.getOrDefault("rels", List.of());
        @SuppressWarnings("unchecked") List<Node> persons = (List<Node>) row.getOrDefault("persons", List.of());

        addNode(nodes, indexOf, c);
        for (Node p : persons) addNode(nodes, indexOf, p);
        for (Relationship r : rs) addRel(rels, indexOf, r);

        return GenericGraph.builder().nodes(nodes).relationships(rels).build();
    }

    private GenericGraph exportDocketGeneric(String docketId) {
        List<GenericNode> nodes = new ArrayList<>();
        List<GenericRelationship> rels = new ArrayList<>();
        Map<String,Integer> indexOf = new LinkedHashMap<>();

        var row = neo4j.query("""
                MATCH (d:Docket {id:$id})-[rc:CONTAINS]->(c:Case)
                OPTIONAL MATCH (c)-[r:ASSIGNED_TO|REVIEWS]->(p)
                RETURN d AS d, collect(DISTINCT rc) AS rcs, collect(DISTINCT c) AS cases,
                collect(DISTINCT r) AS rs, collect(DISTINCT p) AS persons
                """)
                .bind(docketId).to("id")
                .fetch().one().orElse(Map.of());

        if (row.isEmpty()) {
            return GenericGraph.builder().build();
        }

        Node d = (Node) row.get("d");
        @SuppressWarnings("unchecked") List<Relationship> rcs = (List<Relationship>) row.getOrDefault("rcs", List.of());
        @SuppressWarnings("unchecked") List<Node> cases = (List<Node>) row.getOrDefault("cases", List.of());
        @SuppressWarnings("unchecked") List<Relationship> rs = (List<Relationship>) row.getOrDefault("rs", List.of());
        @SuppressWarnings("unchecked") List<Node> persons = (List<Node>) row.getOrDefault("persons", List.of());

        addNode(nodes, indexOf, d);
        for (Node c : cases) addNode(nodes, indexOf, c);
        for (Node p : persons) addNode(nodes, indexOf, p);
        for (Relationship rc : rcs) addRel(rels, indexOf, rc);
        for (Relationship r : rs) addRel(rels, indexOf, r);

        return GenericGraph.builder().nodes(nodes).relationships(rels).build();
    }

    private GenericNode mapPersonToGeneric(Person p) {
        List<String> labels = new ArrayList<>(List.of("Person"));
        Map<String,Object> props = new LinkedHashMap<>();
        props.put("name", p.getName());
        if (p instanceof Judge j) { labels.add("Judge"); props.put("court", j.getCourt()); }
        if (p instanceof Lawyer l) { labels.add("Lawyer"); props.put("firm", l.getFirm()); }
        GenericNode n = new GenericNode();
        n.setId(p.getId());
        n.setLabels(labels);
        n.setProps(props);
        return n;
    }

    private GenericGraph projectIdsBack(GenericGraph original, List<GenericNode> saved) {
        for (int i = 0; i < original.getNodes().size(); i++) {
            original.getNodes().get(i).setId(saved.get(i).getId());
        }
        return original;
    }


    private void mergeNode(GenericNode n) {
        if (n.getId() == null || n.getId().isBlank()) {
            GenericNode saved = genericService.saveNode(n);
            n.setId(saved.getId());
            return;
        }
        String setLabels = String.join(":", n.getLabels());
        String cypher = "MATCH (x {id:$id}) SET x:" + setLabels + " SET x += $props";
        neo4j.query(cypher)
                .bind(n.getId()).to("id")
                .bind(n.getProps() == null ? Map.of() : n.getProps()).to("props")
                .run();
    }

    private void addNode(List<GenericNode> nodes, Map<String,Integer> indexOf, Node n) {
        String id = (String) n.asMap().get("id");
        if (!indexOf.containsKey(id)) {
            GenericNode gn = new GenericNode();
            gn.setId(id);
            gn.setLabels(new ArrayList<>(n.labels()));
            gn.setProps(new LinkedHashMap<>(n.asMap()));
            nodes.add(gn);
            indexOf.put(id, nodes.size()-1);
        }
    }

    private void addRel(List<GenericRelationship> rels, Map<String,Integer> indexOf, Relationship r) {
// Resolve start/end nodes to property 'id' via a small query using internal rel id
        var row = neo4j.query("MATCH (a)-[x]->(b) WHERE id(x) = $rid RETURN a AS a, b AS b")
                .bind(r.id()).to("rid")
                .fetch().one().orElse(Map.of());
        if (row.isEmpty()) return;
        Node a = (Node) row.get("a");
        Node b = (Node) row.get("b");
        String fromId = (String) a.asMap().get("id");
        String toId = (String) b.asMap().get("id");
        Integer fromIdx = indexOf.get(fromId);
        Integer toIdx = indexOf.get(toId);
        if (fromIdx == null || toIdx == null) return;
        GenericRelationship gr = GenericRelationship.builder()
                .fromIndex(fromIdx)
                .toIndex(toIdx)
                .type(r.type())
                .props(new LinkedHashMap<>(r.asMap()))
                .build();
        rels.add(gr);
    }

    private static String escapeType(String s) { return "`" + s.replace("`", "") + "`"; }
}