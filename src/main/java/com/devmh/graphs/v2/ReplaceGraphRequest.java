package com.devmh.graphs.v2;

import lombok.Data;

import java.util.List;

@Data
public class ReplaceGraphRequest {
    private String description;
    private List<CaseNode> cases;
    private List<CaseRelationship> relationships;
}
