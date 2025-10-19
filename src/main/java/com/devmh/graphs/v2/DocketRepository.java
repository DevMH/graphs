package com.devmh.graphs.v2;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocketRepository extends Neo4jRepository<Docket, String> {

    Optional<Docket> findByName(String name);

    @Query("MATCH (d:Docket)-[:HAS_VERSION]->(dv:DocketVersion) " +
            "WHERE d.uuid = $uuid " +
            "RETURN d, collect(dv) " +
            "ORDER BY dv.versionNumber DESC")
    Optional<Docket> findByUuidWithVersions(@Param("uuid") String uuid);

    @Query("MATCH (d:Docket) WHERE d.name CONTAINS $searchTerm RETURN d")
    List<Docket> searchByName(@Param("searchTerm") String searchTerm);
}
