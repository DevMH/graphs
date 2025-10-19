package com.devmh.graphs.v2;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DocketDTO {
    private String uuid;
    private String name;
    private LocalDateTime createdAt;
    private List<Integer> versionNumbers;
}
