package io.github.agentic.spring.ai.studio.admin.service;

import io.github.agentic.spring.ai.studio.admin.common.PageResult;
import io.github.agentic.spring.ai.studio.admin.dto.Dataset;
import io.github.agentic.spring.ai.studio.admin.dto.request.DatasetCreateRequest;
import io.github.agentic.spring.ai.studio.admin.dto.request.DatasetListRequest;
import io.github.agentic.spring.ai.studio.admin.dto.request.DatasetUpdateRequest;
import io.github.agentic.spring.ai.studio.admin.entity.DatasetDO;

public interface DatasetService {

    /**
     * 创建评测集
     */
    Dataset create(DatasetCreateRequest request);

    /**
     * 分页查询评测集列表
     */
    PageResult<Dataset> list(DatasetListRequest request);

    /**
     * 根据ID获取评测集
     */
    Dataset getById(Long id);

    /**
     * 更新评测集
     */
    Dataset update(DatasetUpdateRequest request);

    /**
     * 根据ID删除评测集
     */
    void deleteById(Long id);
} 
