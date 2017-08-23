package com.meidusa.venus.backend.invoker;

import com.meidusa.venus.backend.services.Endpoint;
import com.meidusa.venus.backend.services.EndpointInvocation;
import com.meidusa.venus.backend.services.InterceptorMapping;
import com.meidusa.venus.backend.services.RequestContext;
import com.meidusa.venus.backend.support.UtilTimerStack;
import com.meidusa.venus.exception.ServiceInvokeException;
import com.meidusa.venus.notify.InvocationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

/**
 * 
 * @author Struct
 * TODO 统一invoker接口定义
 */
public class VenusServerEndpointInvocation implements EndpointInvocation {

    private static Logger logger = LoggerFactory.getLogger(VenusServerEndpointInvocation.class);

    private static String ENDPOINT_INVOKED = "handleRequest endpoint: ";
    
    /**
     * 拦截器列表
     */
    protected Iterator<InterceptorMapping> interceptors;

    /**
     * 是否已经执行
     */
    private boolean executed;

    /**
     * 执行结果
     */
    private Object result;

    /**
     * 相关的endpoint
     */
    private Endpoint endpoint;

    /**
     * 请求上下文
     */
    private RequestContext context;

    /**
     * 返回类型
     */
    private ResultType type = ResultType.RESPONSE;

    public VenusServerEndpointInvocation(RequestContext context, Endpoint endpoint) {
        this.endpoint = endpoint;
        if (endpoint.getInterceptorStack() != null) {
            interceptors = endpoint.getInterceptorStack().getInterceptors().iterator();
        }
        this.context = context;
    }

    @Override
    public RequestContext getContext() {
        return context;
    }

    public ResultType getType() {
        return type;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public Object invoke() {
        //TODO 统一interceptor、filter一致实现，合并proxy/invoker
        if (executed) {
            throw new IllegalStateException("Request has already executed");
        }

        Endpoint ep = this.getEndpoint();

        if (interceptors != null && interceptors.hasNext()) {
            final InterceptorMapping interceptor = interceptors.next();
            String interceptorMsg = "filte: " + interceptor.getName();
            UtilTimerStack.push(interceptorMsg);
            try {
                result = interceptor.getInterceptor().intercept(VenusServerEndpointInvocation.this);
            } finally {
                UtilTimerStack.pop(interceptorMsg);
            }
        } else {
            try {
                UtilTimerStack.push(ENDPOINT_INVOKED);
                Object[] parameters = getContext().getEndPointer().getParameterValues(getContext().getParameters());

                if (ep.isAsync()) {
                    this.type = ResultType.NONE;
                }

                for (Object object : parameters) {
                    if (object instanceof InvocationListener) {
                        this.type = ResultType.NOTIFY;
                    }
                }
                result = ep.getMethod().invoke(ep.getService().getInstance(), parameters);
                logger.info("result:{}.",result);
            } catch (IllegalArgumentException e) {
                throw new ServiceInvokeException(e);
            } catch (InvocationTargetException e) {
                if (e.getTargetException() != null) {
                    throw new ServiceInvokeException(e.getTargetException());
                } else {
                    throw new ServiceInvokeException(e);
                }
            } catch (IllegalAccessException e) {
                throw new ServiceInvokeException(e);
            }finally {
                UtilTimerStack.pop(ENDPOINT_INVOKED);
            }
        }

        if (!executed) {
            executed = true;
        }
        return result;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

}
