package com.devmh.graphs.v1;

import java.util.List;

public record CaseVersionGraph(List<String> teamIds, List<EdgeView> edges) {

}
