package com.commerce.interceptor;

import org.hibernate.resource.jdbc.spi.StatementInspector;

public class QueryCountStatementInterceptor implements StatementInspector {
	@Override
	public String inspect(String sql) {
		RequestContext context = RequestContextHolder.getContext();
		if (context != null) {
			// 쿼리 카운트 증가
			context.incrementQueryCount(sql);
		}

		return sql;
	}
}
