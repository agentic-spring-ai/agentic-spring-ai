package io.github.agentic.spring.ai.studio.admin.service;

import io.github.agentic.spring.ai.studio.admin.dto.ChatSession;
import io.github.agentic.spring.ai.studio.admin.dto.PromptRunResponse;
import io.github.agentic.spring.ai.studio.admin.dto.request.PromptRunRequest;
import reactor.core.publisher.Flux;

public interface PromptRunService {

    /**
     * 运行Prompt调试（支持持续交互）
     *
     * @param request 调试请求
     * @return 流式响应，包含会话信息
     */
    Flux<PromptRunResponse> run(PromptRunRequest request);

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话对象
     */
    ChatSession getSession(String sessionId);

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     */
    void deleteSession(String sessionId);
}
