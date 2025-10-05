package com.devmh.graphs.typed;

import com.devmh.graphs.generic.GenericNode;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class GenericToTypedMapper {
    public Person toTypedPerson(GenericNode node) {
        Map<String,Object> p = node.getProps();
        if (node.getLabels().contains("Judge")) {
            Judge j = new Judge();
            j.setName((String)p.get("name"));
            j.setCourt((String)p.get("court"));
            return j;
        }
        if (node.getLabels().contains("Lawyer")) {
            Lawyer l = new Lawyer();
            l.setName((String)p.get("name"));
            l.setFirm((String)p.get("firm"));
            return l;
        }
        throw new IllegalArgumentException("Unsupported Person labels: " + node.getLabels());
    }

    public Case toTypedCase(GenericNode node) {
        Case c = new Case();
        c.setName((String) node.getProps().get("name"));
        return c;
    }

    public Docket toTypedDocket(GenericNode node) {
        Docket d = new Docket();
        d.setNumber((String) node.getProps().get("number"));
        return d;
    }
}
