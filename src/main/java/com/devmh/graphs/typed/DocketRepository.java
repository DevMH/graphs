package com.devmh.graphs.typed;

import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface DocketRepository extends Neo4jRepository<Docket, String> { }
