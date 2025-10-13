package com.devmh.graphs.v1;

import java.util.Set;

public record GraphDelta(
        Set<String> addTeams,
        Set<String> removeTeams,
        Set<EdgeView> addEdges,
        Set<EdgeView> removeEdges
) {
    public static GraphDelta empty() {
        return new GraphDelta(Set.of(), Set.of(), Set.of(), Set.of());
    }
}
