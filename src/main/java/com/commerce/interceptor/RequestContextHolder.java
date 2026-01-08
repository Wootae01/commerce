package com.commerce.interceptor;

public class RequestContextHolder {

	private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

	public static void initContext(RequestContext context) {
		CONTEXT.remove();
		CONTEXT.set(context);
	}

	public static RequestContext getContext() {
		return CONTEXT.get();
	}

	public static void clear() {
		CONTEXT.remove();
	}

}
