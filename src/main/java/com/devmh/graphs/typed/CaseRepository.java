package com.devmh.graphs.typed;

import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface CaseRepository extends Neo4jRepository<Case, String> { }
