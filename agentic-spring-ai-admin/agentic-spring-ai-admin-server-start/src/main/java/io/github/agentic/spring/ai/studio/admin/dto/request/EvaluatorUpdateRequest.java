package io.github.agentic.spring.ai.studio.admin.dto.request;


import lombok.Data;

@Data
public class EvaluatorUpdateRequest {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 评估器名称
     */
    private String name;


    /**
     * 评估器描述
     */
    private String description;


}
