package com.devmh.graphs.generic;

import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface GenericNodeRepository extends Neo4jRepository<GenericNode, String> {
}
