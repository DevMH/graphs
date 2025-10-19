package com.devmh.graphs.v2;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocketVersionRepository extends Neo4jRepository<DocketVersion, String> {

    @Query("MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion) " +
            "RETURN dv ORDER BY dv.versionNumber DESC")
    List<DocketVersion> findByDocketUuid(@Param("docketUuid") String docketUuid);

    @Query("MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion {versionNumber: $versionNumber}) " +
            "RETURN dv")
    Optional<DocketVersion> findByDocketUuidAndVersionNumber(
            @Param("docketUuid") String docketUuid,
            @Param("versionNumber") Integer versionNumber);

    @Query("MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion) " +
            "WHERE dv.isActive = true " +
            "RETURN dv ORDER BY dv.versionNumber DESC LIMIT 1")
    Optional<DocketVersion> findActiveDocketVersion(@Param("docketUuid") String docketUuid);

    @Query("MATCH (d:Docket {uuid: $docketUuid})-[:HAS_VERSION]->(dv:DocketVersion) " +
            "RETURN MAX(dv.versionNumber)")
    Optional<Integer> findMaxVersionNumber(@Param("docketUuid") String docketUuid);
}