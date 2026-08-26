package io.github.agentic.spring.ai.studio.admin.dto.request;

import io.github.agentic.spring.ai.studio.admin.dto.DatasetColumn;
import lombok.Data;

import java.util.List;

@Data
public class DatasetVersionUpdateRequest {

    /**
     * 数据集版本Id描述
     */
    private Long datasetVersionId;


    /**
     * 数据集版本描述
     */
    private String description;


    /**
     * 数据集版本状态
     */

    private String status;

} 
