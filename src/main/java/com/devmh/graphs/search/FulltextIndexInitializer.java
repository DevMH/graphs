package com.devmh.graphs.search;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableConfigurationProperties(FulltextIndexProps.class)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "graphs.search.fulltext", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FulltextIndexInitializer {
    private final Neo4jClient neo4j;
    private final FulltextIndexProps props;

    @PostConstruct
    public void createIfMissing() {
        String labels = String.join("|", props.getLabels());
        String properties = props.getProperties().stream().map(p -> "n." + p).collect(Collectors.joining(", "));
        String cypher = "CREATE FULLTEXT INDEX " + props.getIndexName() + " IF NOT EXISTS FOR (n:" + labels + ") ON EACH [" + properties + "]";
        log.info("Ensuring fulltext index exists: {}", cypher);
        neo4j.query(cypher).run();
        neo4j.query("CALL db.awaitIndexes(300000)").run();
    }
}