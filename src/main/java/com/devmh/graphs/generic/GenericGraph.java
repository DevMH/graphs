package com.devmh.graphs.generic;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericGraph {
    @Builder.Default
    private List<GenericNode> nodes = new ArrayList<>();
    @Builder.Default
    private List<GenericRelationship> relationships = new ArrayList<>();
}
