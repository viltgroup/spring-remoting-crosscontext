package com.vilt.spring.remoting.crosscontext;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.remoting.support.RemoteInvocationBasedExporter;
import org.springframework.remoting.support.RemoteInvocationResult;
import org.springframework.web.HttpRequestHandler;

public class CrossContextServiceExporter extends RemoteInvocationBasedExporter implements InitializingBean, DisposableBean, HttpRequestHandler {
	
	private SerializingConverter serializingConverter = new SerializingConverter();
	private DeserializingConverter deserializingConverter = new DeserializingConverter();
	
	private boolean serialize = true;
	
	public void destroy() throws Exception {
		// nothing to do here...
	}

	public void afterPropertiesSet() throws Exception {
		// nothing to do here...
	}

	public void setSerialize(boolean serialize) {
		this.serialize = serialize;
	}
	
	public boolean isSerialize() {
		return serialize;
	}
	
	public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		Object value = request.getAttribute(CrossContextProxyBeanFactory.INVOCATION_ATTR);
		
		if (value == null) {
			throw new IllegalStateException("No Spring invocation found. This must be called by a " +
					"com.vilt.spring.remoting.crosscontext.CrossContextProxyBeanFactory instance or a subclass.");
		}
		
		RemoteInvocation call;
		if (serialize) {
			if (!(value instanceof byte[])) {
				throw new IllegalStateException("A serialized object was expected. Ensure that the " +
						"com.vilt.spring.remoting.crosscontext.CrossContextProxyBeanFactory on the other side is configured with" +
						"serialize=\"true\".");
			}
			
			call = (RemoteInvocation) deserializingConverter.convert((byte[]) value);
		}
		else {
			call = (RemoteInvocation) value;
		}
		
		RemoteInvocationResult result;
		
		try {
			result = new RemoteInvocationResult(call.invoke(getService()));
		} catch (InvocationTargetException e) {
			result = new RemoteInvocationResult(e.getTargetException());
		} catch (Throwable e) {
			result = new RemoteInvocationResult(e);
		}
		
		request.setAttribute(CrossContextProxyBeanFactory.INVOCATION_RESULT_ATTR, serialize ? serializingConverter.convert(result) : result);
	}
}
