package com.commerce.interceptor;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class QueryCountInterceptor implements HandlerInterceptor {

	private static final String UNKNOWN_PATH = "UNKNOWN_PATH";
	private final MeterRegistry meterRegistry;


	public QueryCountInterceptor(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	// RequestContext 생성 후 ThreadLocal에 등록
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws
		Exception {

		String method = request.getMethod();
		String bestMatchPath = (String)request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

		if (bestMatchPath == null) {
			bestMatchPath = UNKNOWN_PATH;
		}

		RequestContext context = RequestContext.builder()
			.bestMatchPath(bestMatchPath)
			.httpMethod(method)
			.build();

		RequestContextHolder.initContext(context);
		return true;
	}

	// query count 를 meterRegistry 에 등록 후 ThreadLocal 정리
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
		Exception ex) throws Exception {

		RequestContext context = RequestContextHolder.getContext();
		if (context != null) {
			Map<QueryType, Integer> queryCountByType = context.getQueryCountByType();
			queryCountByType.forEach(((queryType, count) -> increment(context, queryType, count)));
		}

		RequestContextHolder.clear();
	}
	private void increment(RequestContext ctx, QueryType queryType, Integer count) {
		DistributionSummary summary = DistributionSummary.builder("app.query.per_request")
			.description("Number of SQL queries per request")
			.tag("path", ctx.getBestMatchPath())
			.tag("http_method", ctx.getHttpMethod())
			.tag("query_type", queryType.name())
			.publishPercentiles(0.5, 0.95)
			.register(meterRegistry);

		summary.record(count);
	}
}
