package com.devmh.graphs.v2;

import lombok.Data;

@Data
public class CaseRelationship {
    private String fromCaseUuid;
    private String toCaseUuid;
}
