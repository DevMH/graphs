package com.devmh.graphs.v2;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends Neo4jRepository<Case, String> {

    Optional<Case> findByName(String name);

    @Query("MATCH (c:Case) WHERE c.name CONTAINS $searchTerm RETURN c")
    List<Case> searchByName(@Param("searchTerm") String searchTerm);

    @Query("MATCH (c:Case) WHERE c.uuid IN $uuids RETURN c")
    List<Case> findByUuidIn(@Param("uuids") List<String> uuids);
}