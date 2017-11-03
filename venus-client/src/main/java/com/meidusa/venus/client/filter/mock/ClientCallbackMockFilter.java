package com.meidusa.venus.client.filter.mock;

import com.meidusa.venus.*;
import com.meidusa.venus.ClientInvocation;
import com.meidusa.venus.exception.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 回调放通处理
 * Created by Zhangzhihua on 2017/8/1.
 */
public class ClientCallbackMockFilter implements Filter {

    private static Logger logger = LoggerFactory.getLogger(ClientCallbackMockFilter.class);

    //降级类型-return
    static final String MOCK_TYPE_RETURN = "MOCK_TYPE_RETURN ";
    //降级类型-throw
    static final String MOCK_TYPE_THROW = "MOCK_TYPE_THROW";
    //降级类型-callback
    static final String MOCK_TYPE_CALLBACK = "MOCK_TYPE_CALLBACK";

    @Override
    public void init() throws RpcException {

    }

    @Override
    public Result beforeInvoke(Invocation invocation, URL url) throws RpcException {
        ClientInvocation clientInvocation = (ClientInvocation)invocation;
        if(!isEnableCallbackMock(clientInvocation, url)){
            return null;
        }
        //获取mock callback TODO 注入instance及动态构造method并传参
        Method callbackMethod = null;
        Object instance = null;
        Object[] args = null;
        try {
            Object retur = callbackMethod.invoke(instance,args);
            return new Result(retur);
        } catch (Exception e) {
            Result result = new Result();
            result.setException(e);
            return result;
        }
    }

    /**
     * 判断是否开启callback放通
     * @param invocation
     * @param url
     * @return
     */
    boolean isEnableCallbackMock(ClientInvocation invocation, URL url){
       return false;
    }

    @Override
    public Result throwInvoke(Invocation invocation, URL url, Throwable e) throws RpcException {
        return null;
    }

    @Override
    public Result afterInvoke(Invocation invocation, URL url) throws RpcException {
        return null;
    }

    @Override
    public void destroy() throws RpcException {

    }

}
