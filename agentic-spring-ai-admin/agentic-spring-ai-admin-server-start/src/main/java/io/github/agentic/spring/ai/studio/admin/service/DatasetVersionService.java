package io.github.agentic.spring.ai.studio.admin.service;

import io.github.agentic.spring.ai.studio.admin.common.PageResult;
import io.github.agentic.spring.ai.studio.admin.dto.DatasetVersion;
import io.github.agentic.spring.ai.studio.admin.dto.Experiment;
import io.github.agentic.spring.ai.studio.admin.dto.request.DatasetExperimentsListRequest;
import io.github.agentic.spring.ai.studio.admin.dto.request.DatasetVersionCreateRequest;
import io.github.agentic.spring.ai.studio.admin.dto.request.DatasetVersionListRequest;
import io.github.agentic.spring.ai.studio.admin.dto.request.DatasetVersionUpdateRequest;
import io.github.agentic.spring.ai.studio.admin.entity.DatasetVersionDO;
import org.springframework.stereotype.Service;


@Service

public interface DatasetVersionService {

    DatasetVersion create(DatasetVersionCreateRequest request);

    PageResult<DatasetVersion> list(DatasetVersionListRequest request);

    DatasetVersion update(DatasetVersionUpdateRequest request);
    
    DatasetVersion getById(Long id);
    
    void deleteById(Long id);

    PageResult<Experiment> getExperiments(DatasetExperimentsListRequest datasetExperimentsListRequest);
}
