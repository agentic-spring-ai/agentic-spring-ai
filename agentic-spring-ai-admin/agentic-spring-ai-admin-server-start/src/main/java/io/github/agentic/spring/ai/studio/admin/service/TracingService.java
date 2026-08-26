package io.github.agentic.spring.ai.studio.admin.service;

import io.github.agentic.spring.ai.studio.admin.common.PageResult;
import io.github.agentic.spring.ai.studio.admin.dto.*;
import io.github.agentic.spring.ai.studio.admin.dto.request.OverviewQueryRequest;
import io.github.agentic.spring.ai.studio.admin.dto.request.ServicesQueryRequest;
import io.github.agentic.spring.ai.studio.admin.dto.request.TracesQueryRequest;

public interface TracingService {

    /**
     * 分页查询追踪列表
     */
    PageResult<TraceSpanDTO> queryTraces(TracesQueryRequest request);

    /**
     * 根据TraceId获取追踪详情
     */
    TraceDetailDTO getTraceDetail(String traceId);

    /**
     * 获取服务列表
     */
    ServicesResponseDTO getServices(ServicesQueryRequest request);

    /**
     * 获取概览信息
     */
    OverviewStatsDTO getOverview(OverviewQueryRequest request);
}
