package io.github.agentic.spring.ai.studio.admin.service;

import io.github.agentic.spring.ai.studio.admin.common.PageResult;
import io.github.agentic.spring.ai.studio.admin.dto.EvaluatorTemplate;
import io.github.agentic.spring.ai.studio.admin.dto.request.EvaluatorTemplateListRequest;

public interface EvaluatorTemplateService {


    
    EvaluatorTemplate get(Long id);
    

    PageResult<EvaluatorTemplate> list(EvaluatorTemplateListRequest request);
}
