package com.meidusa.venus;

import com.meidusa.venus.annotations.Endpoint;
import com.meidusa.venus.annotations.Service;
import com.meidusa.venus.metainfo.EndpointParameter;
import com.meidusa.venus.notify.InvocationListener;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * 请求对象
 * Created by Zhangzhihua on 2017/7/31.
 */
public class Invocation {

    private byte[] traceID;

    private byte[] messageId;

    private Class<?> serviceType;

    private Method method;

    private Object[] args;

    private Service service;

    private Endpoint endpoint;

    private EndpointParameter[] params;

    private InvocationListener invocationListener;

    private Type type;

    boolean async;

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    public Class<?> getServiceType() {
        return serviceType;
    }

    public void setServiceType(Class<?> serviceType) {
        this.serviceType = serviceType;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public EndpointParameter[] getParams() {
        return params;
    }

    public void setParams(EndpointParameter[] params) {
        this.params = params;
    }

    public byte[] getTraceID() {
        return traceID;
    }

    public void setTraceID(byte[] traceID) {
        this.traceID = traceID;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public byte[] getMessageId() {
        return messageId;
    }

    public void setMessageId(byte[] messageId) {
        this.messageId = messageId;
    }

    public InvocationListener getInvocationListener() {
        return invocationListener;
    }

    public void setInvocationListener(InvocationListener invocationListener) {
        this.invocationListener = invocationListener;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
}
