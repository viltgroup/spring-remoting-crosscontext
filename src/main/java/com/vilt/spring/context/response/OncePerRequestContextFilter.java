/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vilt.spring.context.response;

import static java.lang.Boolean.TRUE;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.filter.GenericFilterBean;

/**
 * Filter base class that guarantees to be just executed once per request, on
 * any servlet container. It provides a {@link #doFilterInternal} method with
 * HttpServletRequest and HttpServletResponse arguments.
 * 
 * <p>
 * The {@link #getAlreadyFilteredAttributeName} method determines how to
 * identify that a request is already filtered. The default implementation is
 * based on the configured name of the concrete filter instance.
 * 
 * @author Juergen Hoeller
 * @author Ricardo Santos
 * @since 27.03.2014
 */
public abstract class OncePerRequestContextFilter extends GenericFilterBean {

	/**
	 * Suffix that gets appended to both the filter name and servlet context for
	 * the "already filtered" request attribute.
	 * 
	 * @see #getAlreadyFilteredAttributeName
	 */
	public static final String ALREADY_FILTERED_SUFFIX = ".FILTERED";

	/**
	 * This <code>doFilter</code> implementation stores a request attribute for
	 * "already filtered", proceeding without filtering again if the attribute
	 * is already there.
	 * 
	 * @see #getAlreadyFilteredAttributeName
	 * @see #doFilterInternal
	 */
	public final void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
			throw new ServletException("OncePerRequestContextFilter just supports HTTP requests");
		}
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String alreadyFilteredAttributeName = getAlreadyFilteredAttributeName();
		if (request.getAttribute(alreadyFilteredAttributeName) != null) {
			// Proceed without invoking this filter...
			filterChain.doFilter(request, response);
			return;
		}
		// Do invoke this filter...
		request.setAttribute(alreadyFilteredAttributeName, TRUE);
		try {
			doFilterInternal(httpRequest, httpResponse, filterChain);
		} finally {
			// Remove the "already filtered" request attribute for this
			// request.
			request.removeAttribute(alreadyFilteredAttributeName);
		}
	}

	/**
	 * Return the name of the request attribute that identifies that a request
	 * is already filtered.
	 * <p>
	 * Default implementation appends the configured name of the concrete filter
	 * instance, the configured servlet context name and "FILTERED" separated by
	 * dots. If the filter is not fully initialized, it falls back to its class
	 * name.
	 * 
	 * @see #getFilterName
	 * @see #ALREADY_FILTERED_SUFFIX
	 */
	protected String getAlreadyFilteredAttributeName() {
		String name = getFilterName();
		if (name == null) {
			name = getClass().getName();
		}
		return name + "." + getServletContext() + ALREADY_FILTERED_SUFFIX;
	}

	/**
	 * Same contract as for <code>doFilter</code>, but guaranteed to be just
	 * invoked once per request context. Provides HttpServletRequest and
	 * HttpServletResponse arguments instead of the default ServletRequest and
	 * ServletResponse ones.
	 */
	protected abstract void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException,
			IOException;

}