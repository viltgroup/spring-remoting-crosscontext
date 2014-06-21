package com.vilt.spring.remoting.crosscontext;

import static java.lang.String.format;

import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.remoting.support.RemoteInvocationBasedAccessor;
import org.springframework.remoting.support.RemoteInvocationResult;
import org.springframework.web.context.ServletContextAware;

import com.vilt.spring.context.response.RequestAndResponseHolder;

/**
 * 
 * Just replace your "web" remoting proxy bean factory by this one (and ensure
 * that on the other side, you use a {@link CrossContextServiceExporter} to
 * expose your service implementation.
 * 
 * <pre>
 * {@code
 * <bean id="myService" class="com.vilt.spring.remoting.crosscontext.CrossContextProxyBeanFactory">
 *   <property name="serviceInterface" value="com.example.MyService" />
 *   <property name="serviceUrl" value="/my/service" />
 * </bean>
 * }
 * </pre>
 * 
 * <strong>Very important:</strong> Spring, by default, cleans up attributes
 * after an include.<br>
 * To ensure that we can get the response after, set cleanupAfterInclude to true
 * in web.xml:
 * 
 * <pre>
 * {@code
 * ...
 * <servlet>
 *   <servlet-name>dispatch</servlet-name>
 *   <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
 *   <init-param>
 *     <param-name>cleanupAfterInclude</param-name>
 *     <param-value>false</param-value>
 *   </init-param>
 *   <load-on-startup>1</load-on-startup>
 * </servlet>
 * ...
 * }
 * </pre>
 * 
 * Also add the following listener to web.xml, on the services sides:
 * 
 * <pre>
 * {@code
 * ...
 * <filter>
 *   <filter-name>requestAndResponseContextFilter</filter-name>
 *   <filter-class>com.vilt.spring.context.response.RequestAndResponseContextFilter</filter-class>
 * </filter>
 * 
 * <filter-mapping>
 *   <filter-name>requestAndResponseContextFilter</filter-name>
 *   <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * ...
 * }
 * </pre>
 * 
 * <p>
 * A normal configuration is to have the services exposed by a
 * BeanNameUrlHandlerMapping and an HttpRequestHandlerAdapter. To ensure this on
 * the dispatcher context xml prepare this two beans. The name is important to
 * be handlerMapping and handlerAdapter:
 * 
 * <pre>
 * {@code
 * ...
 * <bean name="handlerMapping"
 * 	class="org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping"
 * />
 * <bean name="handlerAdapter"
 * 	class="org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter"
 * />
 * ...
 * }
 * </pre>
 * 
 * and on the DispatcherServlet also set both detectAllHandlerMappings and
 * detectAllHandlerAdapters to false:
 * 
 * <pre>
 * {@code
 * ...
 * <servlet>
 * 	<servlet-name>dispatcher</servlet-name>
 * 	<servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
 * ...
 * 	<init-param>
 * 		<param-name>detectAllHandlerMappings</param-name>
 * 		<param-value>false</param-value>
 * 	</init-param>
 * 	<init-param>
 * 		<param-name>detectAllHandlerAdapters</param-name>
 * 		<param-value>false</param-value>
 * 	</init-param>
 * 	<load-on-startup>2</load-on-startup>
 * </servlet>
 * ...
 * }
 * </pre>
 * 
 * @see org.springframework.web.servlet.DispatcherServlet#setCleanupAfterInclude(boolean)
 * @see CrossContextServiceExporter
 * @see com.vilt.spring.remoting.crosscontext.RequestAndResponseContextFilter
 * @see org.springframework.web.servlet.DispatcherServlet
 * 
 * @author Rui
 * @author Ricardo Santos
 * @since 1.0
 */
public class CrossContextProxyBeanFactory extends RemoteInvocationBasedAccessor implements ServletContextAware, MethodInterceptor, FactoryBean<Object>,
		BeanClassLoaderAware {

	public static final String INVOCATION_ATTR = CrossContextProxyBeanFactory.class.getName() + ".INVOCATION";
	public static final String INVOCATION_RESULT_ATTR = CrossContextProxyBeanFactory.class.getName() + ".INVOCATION_RESULT";

	private ServletContext servletContext;

	private final SerializingConverter serializingConverter = new SerializingConverter();
	private final DeserializingConverter deserializingConverter = new DeserializingConverter();
	private boolean serialize = true;
	private Object serviceProxy;

	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		super.setBeanClassLoader(classLoader);
	}

	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		this.serviceProxy = new ProxyFactory(getServiceInterface(), this).getProxy(getBeanClassLoader());
	}

	public Object getObject() throws Exception {
		return serviceProxy;
	}

	public Class<?> getObjectType() {
		return getServiceInterface();
	}

	public boolean isSingleton() {
		return true;
	}

	public void setSerialize(boolean serialize) {
		this.serialize = serialize;
	}

	public boolean isSerialize() {
		return serialize;
	}

	public Object invoke(MethodInvocation invocation) throws Throwable {

		if (servletContext == null) {
			throw new NullPointerException("No servlet context was set. Ensure that Spring is able to inject a servlet context " + "in this bean");
		}

		HttpServletRequest servletRequest = getServletRequest();
		ServletContext remoteContext = servletContext.getContext(getRemoteContextPath());
		if (remoteContext == null) {
			throw new IllegalStateException(format("Servlet context for %s could not be found. Ensure the corresponding context supports cross context calls!",
					remoteContext));
		}

		String contextRelativeServicePath = getServiceUrl().substring(remoteContext.getContextPath().length());

		Method method = invocation.getMethod();
		Object[] arguments = invocation.getArguments();

		RemoteInvocation call = new RemoteInvocation(method.getName(), method.getParameterTypes(), arguments);
		Map<String, Object> backupAttributes = backupAndRemoveAttributes(servletRequest);

		Object value;

		try {
			servletRequest.setAttribute(INVOCATION_ATTR, serialize ? serializingConverter.convert(call) : call);
			RequestDispatcher requestDispatcher = remoteContext.getRequestDispatcher(contextRelativeServicePath);
			requestDispatcher.include(servletRequest, getServletResponse());
		} finally {
			value = servletRequest.getAttribute(INVOCATION_RESULT_ATTR);
			servletRequest.removeAttribute(INVOCATION_ATTR);
			servletRequest.removeAttribute(INVOCATION_RESULT_ATTR);
			restoreAttributes(servletRequest, backupAttributes);
		}

		if (value == null) {
			throw new IllegalStateException(format("Could not get the invocation response. Please ensure that %s is a valid url and that the spring "
					+ "DispatcherServlet serving it doesn't clean attributes after includes "
					+ "(see org.springframework.web.servlet.DispatcherServlet#setCleanupAfterInclude(boolean))", getServiceUrl()));
		}

		RemoteInvocationResult callReturn;
		if (serialize) {
			callReturn = (RemoteInvocationResult) deserializingConverter.convert((byte[]) value);
		} else {
			callReturn = (RemoteInvocationResult) value;
		}

		if (callReturn.getException() != null) {
			throw callReturn.getException();
		}

		return callReturn.getValue();
	}

	protected Map<String, Object> backupAndRemoveAttributes(HttpServletRequest request) {

		Map<String, Object> backupAttributes = new HashMap<String, Object>();

		Enumeration<?> names = request.getAttributeNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.")) {
				continue;
			}
			backupAttributes.put(name, request.getAttribute(name));
		}
		for (Object name : backupAttributes.keySet()) {
			request.removeAttribute((String) name);
		}

		return backupAttributes;
	}

	protected void restoreAttributes(HttpServletRequest request, Map<String, Object> backupAttributes) {
		for (String name : backupAttributes.keySet()) {
			request.setAttribute(name, backupAttributes.get(name));
		}
	}

	protected String getRemoteContextPath() {
		int indexOf = getServiceUrl().indexOf('/', 1);
		if (indexOf == -1) {
			throw new IllegalStateException(format("Could not extract context path from %s", getServiceUrl()));
		}

		return getServiceUrl().substring(0, indexOf);
	}

	protected HttpServletRequest getServletRequest() {
		HttpServletRequest request = RequestAndResponseHolder.getHttpServletRequest();
		if (request == null) {
			throw new IllegalStateException(
					"No valid servlet request attributes bound to this thread. Please ensure you're using this proxy inside a HTTP Servlet and have configured the RequestAndResponseContextFilter.");
		}
		return request;
	}

	protected HttpServletResponse getServletResponse() {
		HttpServletResponse response = RequestAndResponseHolder.getHttpServletResponse();
		if (response == null) {
			throw new IllegalStateException(
					"No valid servlet response attributes bound to this thread. Please ensure you're using this proxy inside a HTTP Servlet and have configured the RequestAndResponseContextFilter.");
		}
		return response;
	}

}
