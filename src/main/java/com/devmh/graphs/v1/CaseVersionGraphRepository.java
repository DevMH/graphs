package com.devmh.graphs.v1;

public interface CaseVersionGraphRepository {
    CaseVersionGraph loadGraph(String caseId, String versionId);
}
