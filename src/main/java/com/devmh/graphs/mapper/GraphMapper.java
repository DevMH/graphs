package com.devmh.graphs.mapper;

import com.devmh.graphs.generic.GenericGraph;
import com.devmh.graphs.generic.GenericNode;
import com.devmh.graphs.generic.GenericRelationship;
import com.devmh.graphs.typed.*;
import org.mapstruct.Mapper;

import java.util.*;

@Mapper(componentModel = "spring")
public interface GraphMapper {

    // ===== Typed -> Generic =====
    default GenericGraph fromTypedCase(Case c) {
        List<GenericNode> nodes = new ArrayList<>();
        List<GenericRelationship> rels = new ArrayList<>();

        int caseIdx = nodes.size();
        nodes.add(nodeFromCase(c));

        for (Person p : Optional.ofNullable(c.getAssignees()).orElseGet(Set::of)) {
            int pIdx = nodes.size();
            nodes.add(nodeFromPerson(p));
            rels.add(GenericRelationship.builder()
                    .fromIndex(caseIdx)
                    .toIndex(pIdx)
                    .type("ASSIGNED_TO")
                    .props(Map.of())
                    .build());
        }
        for (Person p : Optional.ofNullable(c.getReviewers()).orElseGet(Set::of)) {
            int pIdx = nodes.size();
            nodes.add(nodeFromPerson(p));
            rels.add(GenericRelationship.builder()
                    .fromIndex(caseIdx)
                    .toIndex(pIdx)
                    .type("REVIEWS")
                    .props(Map.of())
                    .build());
        }
        return GenericGraph.builder().nodes(nodes).relationships(rels).build();
    }

    default GenericGraph fromTypedDocket(Docket d) {
        List<GenericNode> nodes = new ArrayList<>();
        List<GenericRelationship> rels = new ArrayList<>();

        int docketIdx = nodes.size();
        nodes.add(nodeFromDocket(d));

        for (Case c : Optional.ofNullable(d.getCases()).orElseGet(Set::of)) {
            int cIdx = nodes.size();
            nodes.add(nodeFromCase(c));
            rels.add(GenericRelationship.builder()
                    .fromIndex(docketIdx)
                    .toIndex(cIdx)
                    .type("CONTAINS")
                    .props(Map.of())
                    .build());

            for (Person p : Optional.ofNullable(c.getAssignees()).orElseGet(Set::of)) {
                int pIdx = nodes.size();
                nodes.add(nodeFromPerson(p));
                rels.add(GenericRelationship.builder()
                        .fromIndex(cIdx)
                        .toIndex(pIdx)
                        .type("ASSIGNED_TO")
                        .props(Map.of())
                        .build());
            }
            for (Person p : Optional.ofNullable(c.getReviewers()).orElseGet(Set::of)) {
                int pIdx = nodes.size();
                nodes.add(nodeFromPerson(p));
                rels.add(GenericRelationship.builder()
                        .fromIndex(cIdx)
                        .toIndex(pIdx)
                        .type("REVIEWS")
                        .props(Map.of())
                        .build());
            }
        }
        return GenericGraph.builder().nodes(nodes).relationships(rels).build();
    }

    // ===== Generic -> Typed =====
    default Case toTypedCase(GenericGraph g) {
        int caseIdx = findFirstIndexWithLabel(g.getNodes(), "Case")
                .orElseThrow(() -> new IllegalArgumentException("No Case node in graph"));
        GenericNode caseNode = g.getNodes().get(caseIdx);
        Case c = new Case();
        c.setName((String) caseNode.getProps().get("name"));
        c.setAssignees(new LinkedHashSet<>());
        c.setReviewers(new LinkedHashSet<>());
        for (GenericRelationship r : g.getRelationships()) {
            if (!Objects.equals(r.getFromIndex(), caseIdx)) continue;
            GenericNode target = g.getNodes().get(r.getToIndex());
            switch (r.getType()) {
                case "ASSIGNED_TO" -> c.getAssignees().add(personFromNode(target));
                case "REVIEWS" -> c.getReviewers().add(personFromNode(target));
            }
        }
        return c;
    }

    default Docket toTypedDocket(GenericGraph g) {
        int docketIdx = findFirstIndexWithLabel(g.getNodes(), "Docket")
                .orElseThrow(() -> new IllegalArgumentException("No Docket node in graph"));
        GenericNode docketNode = g.getNodes().get(docketIdx);
        Docket d = new Docket();
        d.setNumber((String) docketNode.getProps().get("number"));
        d.setCases(new LinkedHashSet<>());

        for (GenericRelationship r : g.getRelationships()) {
            if (!Objects.equals(r.getFromIndex(), docketIdx)) continue;
            if (!"CONTAINS".equals(r.getType())) continue;
            int caseIdx = r.getToIndex();
            GenericNode caseNode = g.getNodes().get(caseIdx);
            Case c = new Case();
            c.setName((String) caseNode.getProps().get("name"));
            c.setAssignees(new LinkedHashSet<>());
            c.setReviewers(new LinkedHashSet<>());
            for (GenericRelationship inner : g.getRelationships()) {
                if (!Objects.equals(inner.getFromIndex(), caseIdx)) continue;
                GenericNode target = g.getNodes().get(inner.getToIndex());
                switch (inner.getType()) {
                    case "ASSIGNED_TO" -> c.getAssignees().add(personFromNode(target));
                    case "REVIEWS" -> c.getReviewers().add(personFromNode(target));
                }
            }
            d.getCases().add(c);
        }
        return d;
    }

    // ===== Helpers =====
    private static Optional<Integer> findFirstIndexWithLabel(List<GenericNode> nodes, String label) {
        for (int i = 0; i < nodes.size(); i++) {
            List<String> labels = nodes.get(i).getLabels();
            if (labels != null && labels.contains(label)) return Optional.of(i);
        }
        return Optional.empty();
    }

    private static GenericNode nodeFromCase(Case c) {
        GenericNode n = new GenericNode();
        n.setLabels(new ArrayList<>(List.of("Case")));
        Map<String,Object> props = new LinkedHashMap<>();
        props.put("name", c.getName());
        n.setProps(props);
        return n;
    }

    private static GenericNode nodeFromDocket(Docket d) {
        GenericNode n = new GenericNode();
        n.setLabels(new ArrayList<>(List.of("Docket")));
        Map<String,Object> props = new LinkedHashMap<>();
        props.put("number", d.getNumber());
        n.setProps(props);
        return n;
    }

    private static GenericNode nodeFromPerson(Person p) {
        List<String> labels = new ArrayList<>(List.of("Person"));
        Map<String,Object> props = new LinkedHashMap<>();
        props.put("name", p.getName());
        if (p instanceof Judge j) { labels.add("Judge"); props.put("court", j.getCourt()); }
        if (p instanceof Lawyer l) { labels.add("Lawyer"); props.put("firm", l.getFirm()); }
        GenericNode n = new GenericNode();
        n.setLabels(labels);
        n.setProps(props);
        return n;
    }

    private static Person personFromNode(GenericNode n) {
        Map<String,Object> p = n.getProps();
        if (n.getLabels().contains("Judge")) {
            Judge j = new Judge();
            j.setName((String)p.get("name"));
            j.setCourt((String)p.get("court"));
            return j;
        }
        if (n.getLabels().contains("Lawyer")) {
            Lawyer l = new Lawyer();
            l.setName((String)p.get("name"));
            l.setFirm((String)p.get("firm"));
            return l;
        }
        throw new IllegalArgumentException("Unsupported Person labels: " + n.getLabels());
    }
}