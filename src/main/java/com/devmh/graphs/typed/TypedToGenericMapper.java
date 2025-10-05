package com.devmh.graphs.typed;

import com.devmh.graphs.generic.GenericNode;
import lombok.experimental.UtilityClass;

import java.util.*;

@UtilityClass
public class TypedToGenericMapper {

    public GenericNode toGeneric(Person person) {
        List<String> labels = new ArrayList<>();
        labels.add("Person");
        if (person instanceof Judge) labels.add("Judge");
        if (person instanceof Lawyer) labels.add("Lawyer");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", person.getName());
        if (person instanceof Judge j) props.put("court", j.getCourt());
        if (person instanceof Lawyer l) props.put("firm", l.getFirm());

        return GenericNode.builder().labels(labels).props(props).build();
    }

    public GenericNode toGeneric(Case c) {
        return GenericNode.builder()
                .labels(List.of("Case"))
                .props(Map.of("name", c.getName()))
                .build();
    }

    public GenericNode toGeneric(Docket d) {
        return GenericNode.builder()
                .labels(List.of("Docket"))
                .props(Map.of("number", d.getNumber()))
                .build();
    }
}
