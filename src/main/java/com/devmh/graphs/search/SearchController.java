package com.devmh.graphs.search;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService service;

    @PostMapping("/cypher")
    public Collection<Map<String,Object>> cypher(@RequestBody CypherRequest req) {
        return service.cypher(req.query, req.params);
    }

    @PostMapping("/lucene/nodes")
    public List<SearchService.LuceneNode> luceneNodes(@RequestBody LuceneRequest req) {
        return service.luceneNodes(req.index, req.q, req.limit);
    }

    @Data
    public static class CypherRequest {
        @NotBlank public String query;
        public Map<String,Object> params;
    }

    @Data
    public static class LuceneRequest {
        @NotBlank public String index;
        @NotBlank public String q;
        public Integer limit;
    }
}
