package com.vilt.spring.context.response;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.NamedThreadLocal;

/**
 * Holder class to expose the web request and response in the form of a
 * thread-bound {@link javax.servlet.http.HttpServletRequest} object.
 * 
 * <p>
 * Use {@link RequestAndResponseContextFilter} to expose the current web request
 * and response on any request context.
 * 
 * @author Ricardo Santos
 * @since 2.0
 * @see RequestAndResponseContextFilter
 */
public class RequestAndResponseHolder {
	public RequestAndResponseHolder() {
	}

	private static final ThreadLocal<HttpServletResponse> responseHolder = new NamedThreadLocal<HttpServletResponse>("HttpServletResponse");
	private static final ThreadLocal<HttpServletRequest> requestHolder = new NamedThreadLocal<HttpServletRequest>("HttpServletRequest");

	/**
	 * Bind the given HttpServletRequest to the current thread.
	 * 
	 * @param attributes
	 *            the HttpServletRequest to expose
	 */
	public static void setHttpServletRequest(HttpServletRequest request) {
		requestHolder.set(request);
	}

	/**
	 * Bind the given HttpServletResponse to the current thread.
	 * 
	 * @param attributes
	 *            the HttpServletResponse to expose
	 */
	public static void setHttpServletResponse(HttpServletResponse response) {
		responseHolder.set(response);
	}

	/**
	 * Returns the HttpServletRequest currently bound to the thread.
	 * 
	 * @return the HttpServletRequest currently bound to the thread, or
	 *         <code>null</code> if none bound
	 */
	public static HttpServletRequest getHttpServletRequest() {
		return requestHolder.get();
	}

	/**
	 * Returns the HttpServletResponse currently bound to the thread.
	 * 
	 * @return the HttpServletResponse currently bound to the thread, or
	 *         <code>null</code> if none bound
	 */
	public static HttpServletResponse getHttpServletResponse() {
		return responseHolder.get();
	}

	/**
	 * Resets the Request and Response for the current thread.
	 */
	public static void resetHttpServletRequestAndResponse() {
		responseHolder.remove();
		requestHolder.remove();
	}
}
