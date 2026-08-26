package io.github.agentic.spring.ai.studio.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EvaluatorCreateRequest {

    /**
     * 评估器名称
     */
    @NotNull
    private String name;

    /**
     * 评估器描述
     */
    private String description;



} 
