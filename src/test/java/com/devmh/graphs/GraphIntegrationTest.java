package com.devmh.graphs;

import com.devmh.graphs.generic.GenericGraph;
import com.devmh.graphs.generic.GenericNode;
import com.devmh.graphs.generic.GenericRelationship;
import com.devmh.graphs.generic.GenericGraphService;
import com.devmh.graphs.mapper.GraphMapper;
import com.devmh.graphs.typed.Case;
import com.devmh.graphs.typed.Judge;
import com.devmh.graphs.typed.Lawyer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@Testcontainers
class GraphIntegrationTest {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.21-community")
            .withAdminPassword("secretpwd");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "secretpwd");
    }

    @Autowired GraphMapper mapper;
    @Autowired GenericGraphService genericService;
    @Autowired Neo4jClient neo4jClient;

    @Test
    void typed_to_generic_roundtrip_in_memory() {
        Judge judge = new Judge();
        judge.setName("Test Judge");
        judge.setCourt("Test Court");
        Lawyer lawyer = new Lawyer();
        lawyer.setName("Test Lawyer");
        lawyer.setFirm("Test Firm");

        Case c = new Case();
        c.setName("Test Case");
        c.setAssignees(Set.of(judge));
        c.setReviewers(Set.of(lawyer));

        GenericGraph g = mapper.fromTypedCase(c);
        Case back = mapper.toTypedCase(g);

        assertThat(back.getName()).isEqualTo("Test Case");
        assertThat(back.getAssignees()).hasSize(1);
        assertThat(back.getReviewers()).hasSize(1);
        assertThat(back.getAssignees().iterator().next()).isInstanceOf(Judge.class);
        assertThat(back.getReviewers().iterator().next()).isInstanceOf(Lawyer.class);
    }

    @Test
    void persist_generic_graph_and_verify_relationships() {
        GenericNode caseNode = GenericNode.builder()
                .labels(List.of("Case"))
                .props(Map.of("name", "Test Case"))
                .build();
        GenericNode judgeNode = GenericNode.builder()
                .labels(List.of("Person","Judge"))
                .props(Map.of("name","Test Judge","court","Test Court"))
                .build();
        GenericNode lawyerNode = GenericNode.builder()
                .labels(List.of("Person","Lawyer"))
                .props(Map.of("name","Test Lawyer","firm","Test Firm"))
                .build();

        GenericGraph g = GenericGraph.builder()
                .nodes(List.of(caseNode, judgeNode, lawyerNode))
                .relationships(List.of(
                        GenericRelationship.builder().fromIndex(0).toIndex(1).type("ASSIGNED_TO").build(),
                        GenericRelationship.builder().fromIndex(0).toIndex(2).type("REVIEWS").build()
                ))
                .build();

        List<GenericNode> saved = genericService.saveGraph(g);
        assertThat(saved).hasSize(3);

        long assigned = countRel("ASSIGNED_TO");
        long reviews = countRel("REVIEWS");
        assertThat(assigned).isEqualTo(1);
        assertThat(reviews).isEqualTo(1);
    }

    private long countRel(String type) {
        return neo4jClient.query("MATCH ()-[r:" + type + "]->() RETURN count(r) AS c")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }
}
