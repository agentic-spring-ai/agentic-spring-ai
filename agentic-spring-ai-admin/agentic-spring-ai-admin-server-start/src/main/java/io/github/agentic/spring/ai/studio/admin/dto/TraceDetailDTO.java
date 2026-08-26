package io.github.agentic.spring.ai.studio.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TraceDetailDTO {

    private List<TraceSpanDTO> records;
}
