package com.devmh.graphs.v2;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocketGraphDTO {
    private String docketUuid;
    private String docketName;
    private String versionUuid;
    private Integer versionNumber;
    private String versionDescription;
    private List<CaseNode> cases = new ArrayList<>();
    private List<CaseRelationship> relationships = new ArrayList<>();
}
