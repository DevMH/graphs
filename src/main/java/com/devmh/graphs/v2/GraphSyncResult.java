package com.devmh.graphs.v2;

import lombok.Data;

@Data
public class GraphSyncResult {
    private Integer versionNumber;
    private int casesAdded;
    private int casesRemoved;
    private int casesUpdated;
    private int relationshipsAdded;
    private int relationshipsRemoved;
    private long durationMs;
}
