package com.devmh.graphs;

import com.devmh.graphs.generic.GenericGraph;
import com.devmh.graphs.generic.GenericGraphService;
import com.devmh.graphs.generic.GenericNode;
import com.devmh.graphs.generic.GenericRelationship;
import com.devmh.graphs.search.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@Testcontainers
public class SearchIntegrationTest {
    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.21-community")
            .withAdminPassword("secretpwd");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "secretpwd");
    }

    @Autowired GenericGraphService graphService;
    @Autowired SearchService searchService;

    @Test
    void cypher_and_lucene_search_work() {
        GenericNode caseNode = GenericNode.builder().labels(List.of("Case")).props(Map.of("name","Apollo vs Zeus")).build();
        GenericNode judgeNode = GenericNode.builder().labels(List.of("Person","Judge")).props(Map.of("name","Hon. Ada Lovelace","court","Court of Appeals")).build();
        GenericGraph g = GenericGraph.builder()
                .nodes(List.of(caseNode, judgeNode))
                .relationships(List.of(GenericRelationship.builder().fromIndex(0).toIndex(1).type("ASSIGNED_TO").build()))
                .build();
        graphService.saveGraph(g);

        var rows = searchService.cypher("MATCH (c:Case {name:$name}) RETURN c.name AS name", Map.of("name","Apollo vs Zeus"));
        assertThat(rows).isNotEmpty();
        assertThat(rows.getFirst().get("name")).isEqualTo("Apollo vs Zeus");

        var hits = searchService.luceneNodes("ft_node_all","Apollo", 10);
        assertThat(hits).isNotEmpty();
        Map<?,?> firstNode = hits.getFirst().getNode();
        Map<?,?> props = (Map<?,?>) firstNode.get("props");
        assertThat(props.get("name")).isEqualTo("Apollo vs Zeus");
    }

}
