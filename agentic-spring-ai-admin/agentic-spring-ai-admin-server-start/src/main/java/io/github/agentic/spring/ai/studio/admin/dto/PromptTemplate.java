package io.github.agentic.spring.ai.studio.admin.dto;

import io.github.agentic.spring.ai.studio.admin.entity.PromptTemplateDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplate {

    /**
     * Prompt模板名称
     */
    private String promptTemplateKey;

    /**
     * Prompt描述
     */
    private String templateDescription;

    /**
     * 标签，逗号分隔
     */
    private String tags;

    /**
     * 从DO转换为DTO
     */
    public static PromptTemplate fromDO(PromptTemplateDO promptTemplateDO) {
        if (promptTemplateDO == null) {
            return null;
        }
        return PromptTemplate.builder()
                .promptTemplateKey(promptTemplateDO.getPromptTemplateKey())
                .templateDescription(promptTemplateDO.getTemplateDesc())
                .tags(promptTemplateDO.getTags())
                .build();
    }
}
