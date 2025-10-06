package com.devmh.graphs.search;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "graphs.search.fulltext")
public class FulltextIndexProps {
    private boolean enabled = true;
    private String indexName = "ft_node_all";
    private List<String> labels = Arrays.asList("Case","Docket","Person","Judge","Lawyer");
    private List<String> properties = Arrays.asList("name","number","court","firm");
}
