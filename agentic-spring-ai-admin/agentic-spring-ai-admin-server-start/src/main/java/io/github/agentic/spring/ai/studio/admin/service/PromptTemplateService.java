package io.github.agentic.spring.ai.studio.admin.service;

import io.github.agentic.spring.ai.studio.admin.common.PageResult;
import io.github.agentic.spring.ai.studio.admin.dto.PromptTemplate;
import io.github.agentic.spring.ai.studio.admin.dto.PromptTemplateDetail;
import io.github.agentic.spring.ai.studio.admin.dto.request.PromptTemplateListRequest;
import io.github.agentic.spring.ai.studio.admin.exception.StudioException;

public interface PromptTemplateService {

    /**
     * 根据模板Key获取Prompt模板详情
     *
     * @param promptTemplateKey 模板Key
     * @return Prompt模板详情
     */
    PromptTemplateDetail getByPromptTemplateKey(String promptTemplateKey) throws StudioException;

    /**
     * 分页查询Prompt模板列表
     *
     * @param request 查询请求
     * @return 分页结果
     */
    PageResult<PromptTemplate> list(PromptTemplateListRequest request) throws StudioException;
}
