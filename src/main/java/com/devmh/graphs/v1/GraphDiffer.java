package com.devmh.graphs.v1;

import java.util.HashSet;

public final class GraphDiffer {

    private GraphDiffer() {}

    public static GraphDelta diff(CaseVersionGraph before, CaseVersionGraph after) {
        var beforeTeams = new HashSet<>(before.teamIds());
        var afterTeams  = new HashSet<>(after.teamIds());

        var addTeams = afterTeams.stream()
                .filter(t -> !beforeTeams.contains(t))
                .collect(java.util.stream.Collectors.toSet());
        var removeTeams = beforeTeams.stream()
                .filter(t -> !afterTeams.contains(t))
                .collect(java.util.stream.Collectors.toSet());

        // Edge identity: prefer id when present; otherwise use composite (from|to|kind)
        java.util.function.Function<EdgeView, String> key = e ->
                (e.id() != null && !e.id().isBlank())
                        ? "ID::" + e.id()
                        : "CK::" + e.from() + "|" + e.to() + "|" + e.kind();

        var beforeMap = before.edges().stream().collect(java.util.stream.Collectors.toMap(key, e -> e, (a,b)->a, java.util.LinkedHashMap::new));
        var afterMap  = after.edges().stream().collect(java.util.stream.Collectors.toMap(key, e -> e, (a,b)->a, java.util.LinkedHashMap::new));

        var addEdges = afterMap.keySet().stream()
                .filter(k -> !beforeMap.containsKey(k))
                .map(afterMap::get)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        var removeEdges = beforeMap.keySet().stream()
                .filter(k -> !afterMap.containsKey(k))
                .map(beforeMap::get)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        return new GraphDelta(addTeams, removeTeams, addEdges, removeEdges);
    }
}
