package com.commerce.interceptor;

public enum QueryType {
	SELECT,
	UPDATE,
	DELETE,
	INSERT,
	UNKNOWN;

	public static QueryType from(String sql) {

		if (sql == null || sql.isBlank()) {
			return UNKNOWN;
		}

		String upperCaseSql = sql.trim().toUpperCase();
		if (upperCaseSql.startsWith(QueryType.SELECT.name())) return QueryType.SELECT;
		if (upperCaseSql.startsWith(QueryType.UPDATE.name())) return QueryType.UPDATE;
		if (upperCaseSql.startsWith(QueryType.DELETE.name())) return QueryType.DELETE;
		if (upperCaseSql.startsWith(QueryType.INSERT.name())) return QueryType.INSERT;

		return UNKNOWN;
	}
}
