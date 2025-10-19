package com.devmh.graphs.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocketService {

    private static final Logger logger = LoggerFactory.getLogger(DocketService.class);

    private final DocketRepository docketRepository;
    private final DocketVersionRepository docketVersionRepository;
    private final DocketGraphRepository docketGraphRepository;

    public DocketService(
            DocketRepository docketRepository,
            DocketVersionRepository docketVersionRepository,
            DocketGraphRepository docketGraphRepository) {
        this.docketRepository = docketRepository;
        this.docketVersionRepository = docketVersionRepository;
        this.docketGraphRepository = docketGraphRepository;
    }

    public DocketDTO createDocket(CreateDocketRequest request) {
        Docket docket = new Docket(request.getName());
        Docket saved = docketRepository.save(docket);

        // Create initial version (version 1)
        DocketVersion initialVersion = new DocketVersion(
                1,
                request.getInitialVersionDescription() != null
                        ? request.getInitialVersionDescription()
                        : "Initial version"
        );
        initialVersion.setDocket(saved);
        docketVersionRepository.save(initialVersion);

        return convertToDTO(saved);
    }

    @Transactional(readOnly = true)
    public DocketDTO getDocket(String uuid) {
        Docket docket = docketRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Docket not found with uuid: " + uuid));
        return convertToDTO(docket);
    }

    @Transactional(readOnly = true)
    public DocketDTO getDocketWithVersions(String uuid) {
        Docket docket = docketRepository.findByUuidWithVersions(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Docket not found with uuid: " + uuid));

        List<DocketVersion> versions = docketVersionRepository.findByDocketUuid(uuid);

        DocketDTO dto = convertToDTO(docket);
        dto.setVersionNumbers(versions.stream()
                .map(DocketVersion::getVersionNumber)
                .sorted()
                .collect(Collectors.toList()));

        return dto;
    }

    @Transactional(readOnly = true)
    public List<DocketDTO> getAllDockets() {
        return docketRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public void deleteDocket(String uuid) {
        if (!docketRepository.existsById(uuid)) {
            throw new ResourceNotFoundException("Docket not found with uuid: " + uuid);
        }
        docketRepository.deleteById(uuid);
    }

    private DocketDTO convertToDTO(Docket docket) {
        DocketDTO dto = new DocketDTO();
        dto.setUuid(docket.getUuid());
        dto.setName(docket.getName());
        dto.setCreatedAt(docket.getCreatedAt());
        return dto;
    }
}
