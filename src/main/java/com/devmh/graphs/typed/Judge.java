package com.devmh.graphs.typed;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Node;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Node("Judge")
public class Judge extends Person {
    private String court;
}
