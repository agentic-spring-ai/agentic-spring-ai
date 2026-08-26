package io.github.agentic.spring.ai.studio.admin.service;

import io.github.agentic.spring.ai.studio.admin.common.PageResult;
import io.github.agentic.spring.ai.studio.admin.dto.EvaluatorVersion;
import io.github.agentic.spring.ai.studio.admin.dto.request.EvaluatorVersionCreateRequest;
import io.github.agentic.spring.ai.studio.admin.dto.request.EvaluatorVersionListRequest;
import io.github.agentic.spring.ai.studio.admin.dto.request.EvaluatorVersionUpdateRequest;
import io.github.agentic.spring.ai.studio.admin.entity.EvaluatorVersionDO;

public interface EvaluatorVersionService {

    /**
     * 创建评估器版本
     */
    EvaluatorVersion create(EvaluatorVersionCreateRequest request);

    /**
     * 分页查询评估器列表
     */
    PageResult<EvaluatorVersion>list(EvaluatorVersionListRequest request);

    /**
     * 根据ID获取评估器版本
     */
    EvaluatorVersion getById(Long id);

    /**
     * 更新评估器版本
     */
    EvaluatorVersion update(EvaluatorVersionUpdateRequest request);



} 
