package com.devmh.graphs.v2;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseSnapshotRepository extends Neo4jRepository<CaseSnapshot, String> {

    @Query("MATCH (dv:DocketVersion {uuid: $versionUuid})-[:CONTAINS_CASE]->(cs:CaseSnapshot) " +
            "RETURN cs")
    List<CaseSnapshot> findByDocketVersionUuid(@Param("versionUuid") String versionUuid);

    @Query("MATCH (dv:DocketVersion {uuid: $versionUuid})-[:CONTAINS_CASE]->(cs:CaseSnapshot) " +
            "DETACH DELETE cs")
    void deleteByDocketVersionUuid(@Param("versionUuid") String versionUuid);
}
