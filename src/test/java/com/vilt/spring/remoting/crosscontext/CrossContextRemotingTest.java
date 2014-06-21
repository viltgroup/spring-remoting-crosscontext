package com.vilt.spring.remoting.crosscontext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.Serializable;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockRequestDispatcher;
import org.springframework.mock.web.MockServletContext;

import com.vilt.spring.context.response.RequestAndResponseHolder;

public class CrossContextRemotingTest {

	private static final String RELATIVE_PATH = "/bar";
	private static final String CONTEXT_PATH = "/foo";

	public static class FooException extends Exception {

		private static final long serialVersionUID = 1L;
	}

	public static class MyPojo implements Serializable {
		private static final long serialVersionUID = 1L;

		public String foo;

		public MyPojo() {
		}

		public MyPojo(String foo) {
			this.foo = foo;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof MyPojo && foo.equals(((MyPojo) obj).foo);
		}
	}

	public static interface MyService {
		public MyPojo aMethod(String name) throws FooException;

		public MyPojo anotherMethod(MyPojo pojo);
	}

	public static class MyServiceImpl implements MyService {

		public MyPojo aMethod(String name) throws FooException {
			return new MyPojo(name);
		}

		public MyPojo anotherMethod(MyPojo pojo) {
			return pojo;
		}

	}

	@Test
	public void testCrossContextWithSerialization() throws Exception {
		testCrossContextRemoting(true);
	}

	@Test
	public void testCrossContextWithoutSerialization() throws Exception {
		testCrossContextRemoting(false);
	}

	@SuppressWarnings("unchecked")
	@Test(expected = FooException.class)
	public void testCrossContextWithException() throws Exception {
		MyService impl = mock(MyService.class);
		when(impl.aMethod(anyString())).thenThrow(FooException.class);
		MyService service = getServiceProxy(impl, true);

		service.aMethod("bar"); // throws exception
	}

	private void testCrossContextRemoting(final boolean serialize) throws Exception {
		MyService service = getServiceProxy(new MyServiceImpl(), serialize);

		MyPojo pojo = service.aMethod("bar");
		assertEquals(pojo.foo, "bar");

		MyPojo pojo2 = new MyPojo("foo2");
		MyPojo returnedPojo = service.anotherMethod(pojo2);
		assertEquals(pojo2, returnedPojo);
		// arguments and return values are, by default, serialized
		if (serialize) {
			assertThat(pojo2, not(sameInstance(returnedPojo)));
		} else {
			assertThat(pojo2, sameInstance(returnedPojo));
		}
	}

	private MyService getServiceProxy(final MyService impl, final boolean serialize) throws Exception {
		MockServletContext context = spy(new MockServletContext());
		MockServletContext otherContext = spy(new MockServletContext());
		MockHttpServletRequest request = spy(new MockHttpServletRequest());
		MockHttpServletResponse response = spy(new MockHttpServletResponse());
		MockRequestDispatcher dispatcher = new MockRequestDispatcher(RELATIVE_PATH) {
			@Override
			public void include(ServletRequest request, ServletResponse response) {
				try {
					CrossContextServiceExporter exporter = new CrossContextServiceExporter();
					exporter.setServiceInterface(MyService.class);
					exporter.setService(impl);
					exporter.setSerialize(serialize);
					exporter.handleRequest((HttpServletRequest) request, (HttpServletResponse) response);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};

		when(context.getContext(CONTEXT_PATH)).thenReturn(otherContext);
		when(otherContext.getRequestDispatcher(RELATIVE_PATH)).thenReturn(dispatcher);
		when(otherContext.getContextPath()).thenReturn(CONTEXT_PATH);

		RequestAndResponseHolder.setHttpServletRequest(request);
		RequestAndResponseHolder.setHttpServletResponse(response);

		CrossContextProxyBeanFactory factory = new CrossContextProxyBeanFactory();
		factory.setServiceUrl(CONTEXT_PATH + RELATIVE_PATH);
		factory.setServiceInterface(MyService.class);
		factory.setSerialize(serialize);
		factory.setServletContext(context);
		factory.afterPropertiesSet();

		Object object = factory.getObject();

		assertThat(object, is(MyService.class));
		return (MyService) object;
	}
}
