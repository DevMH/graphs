package com.devmh.graphs.typed;

import com.devmh.graphs.generic.GenericGraphService;
import com.devmh.graphs.generic.GenericNode;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/typed")
@RequiredArgsConstructor
public class TypedGraphController {
    private final CaseRepository caseRepo;
    private final PersonRepository personRepo;
    private final GenericGraphService genericService; // to show typed <-> generic bridging for relationships

    @PostMapping("/demo")
    @Transactional
    public Case createDemo(Case c) {
        Case saved = caseRepo.save(c);
        Judge judge = saved.getAssignees().stream()
                .filter(person -> { return person instanceof Judge; })
                .map(person -> { return (Judge)person; })
                .toList()
                .getFirst();
        Lawyer lawyer = saved.getAssignees().stream()
                .filter(person -> { return person instanceof Lawyer; })
                .map(person -> { return (Lawyer)person; })
                .toList()
                .getFirst();

        GenericNode gCase = genericService.saveNode(TypedToGenericMapper.toGeneric(saved));
        GenericNode gJudge = genericService.saveNode(TypedToGenericMapper.toGeneric(judge));
        GenericNode gLawyer = genericService.saveNode(TypedToGenericMapper.toGeneric(lawyer));

        Map<String,Object> assignedProps = new HashMap<>();
        assignedProps.put("since", "2025-10-04");
        genericService.relate(gCase.getId(), "ASSIGNED_TO", gJudge.getId(), assignedProps);
        genericService.relate(gCase.getId(), "REVIEWS", gLawyer.getId(), Map.of("priority", 1));

        return saved;
    }
}
