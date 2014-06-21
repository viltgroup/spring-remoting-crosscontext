package com.vilt.spring.context.response;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Filter class that binds the current HttpServletRequest and
 * HttpServletResponse to the current thread. This filter exposes the request
 * and response, to all the methods invoked by current thread.
 * 
 * <p>
 * This class differs from
 * {@link org.springframework.web.filter.RequestContextFilter} in that it is
 * invoked once per Servlet Context, exposing the held objects to a portlet or
 * any other included page.
 * 
 * @author Ricardo Santos
 * @since 27.03.2014
 * 
 * @see RequestAndResponseHolder
 * @see OncePerRequestContextFilter
 * @see org.springframework.web.filter.RequestContextFilter
 */
public class RequestAndResponseContextFilter extends OncePerRequestContextFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		RequestAndResponseHolder.setHttpServletRequest(request);
		RequestAndResponseHolder.setHttpServletResponse(response);
		if (logger.isDebugEnabled()) {
			logger.debug("Bound request and response to thread: {" + request + "," + response + "}");
		}
		try {
			filterChain.doFilter(request, response);
		} finally {
			RequestAndResponseHolder.resetHttpServletRequestAndResponse();
			if (logger.isDebugEnabled()) {
				logger.debug("Cleared thread-bound request and response: {" + request + "," + response + "}");
			}
		}
	}

}
