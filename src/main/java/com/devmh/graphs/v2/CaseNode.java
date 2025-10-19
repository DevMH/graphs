package com.devmh.graphs.v2;

import lombok.Data;

@Data
public class CaseNode {
    private String snapshotUuid;
    private String uuid;
    private String name;

    public CaseNode(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public CaseNode() {

    }
}
