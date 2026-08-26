package io.github.agentic.spring.ai.studio.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class EvaluationPromptConfig {
    private String promptKey;

    private String version;

    private List<EvaluationPromptConfigVariableMap> variableMap;

}
