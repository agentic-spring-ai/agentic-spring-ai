package io.github.agentic.spring.ai.studio.admin.dto.request;

import io.github.agentic.spring.ai.studio.admin.dto.DatasetColumn;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DatasetCreateRequest {

    /**
     * 数据集名称
     */
    @NotNull
    private String name;

    /**
     * 数据集描述
     */
    private String description;

    /**
     * 列结构配置
     */
    @NotNull
    private List<DatasetColumn> columnsConfig;



} 
