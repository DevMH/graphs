package com.devmh.graphs.v2;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dockets")
public class DocketController {

    private final DocketService docketService;

    public DocketController(DocketService docketService) {
        this.docketService = docketService;
    }

    @PostMapping
    public ResponseEntity<DocketDTO> createDocket(@RequestBody CreateDocketRequest request) {
        DocketDTO created = docketService.createDocket(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<DocketDTO> getDocket(@PathVariable String uuid) {
        DocketDTO docket = docketService.getDocket(uuid);
        return ResponseEntity.ok(docket);
    }

    @GetMapping("/{uuid}/versions")
    public ResponseEntity<DocketDTO> getDocketWithVersions(@PathVariable String uuid) {
        DocketDTO docket = docketService.getDocketWithVersions(uuid);
        return ResponseEntity.ok(docket);
    }

    @GetMapping
    public ResponseEntity<List<DocketDTO>> getAllDockets() {
        List<DocketDTO> dockets = docketService.getAllDockets();
        return ResponseEntity.ok(dockets);
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deleteDocket(@PathVariable String uuid) {
        docketService.deleteDocket(uuid);
        return ResponseEntity.noContent().build();
    }
}
