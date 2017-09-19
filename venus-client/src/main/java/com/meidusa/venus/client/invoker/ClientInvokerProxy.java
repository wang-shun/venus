package com.meidusa.venus.client.invoker;

import com.athena.service.api.AthenaDataService;
import com.meidusa.venus.*;
import com.meidusa.venus.annotations.Endpoint;
import com.meidusa.venus.annotations.Service;
import com.meidusa.venus.ClientInvocation;
import com.meidusa.venus.client.authenticate.DummyAuthenticator;
import com.meidusa.venus.client.factory.xml.config.ClientRemoteConfig;
import com.meidusa.venus.client.filter.limit.ClientActivesLimitFilter;
import com.meidusa.venus.client.filter.limit.ClientTpsLimitFilter;
import com.meidusa.venus.client.filter.mock.ClientCallbackMockFilter;
import com.meidusa.venus.client.filter.mock.ClientReturnMockFilter;
import com.meidusa.venus.client.filter.mock.ClientThrowMockFilter;
import com.meidusa.venus.client.filter.valid.ClientValidFilter;
import com.meidusa.venus.client.invoker.injvm.InjvmClientInvoker;
import com.meidusa.venus.exception.VenusExceptionFactory;
import com.meidusa.venus.monitor.athena.filter.ClientAthenaMonitorFilter;
import com.meidusa.venus.monitor.filter.ClientMonitorFilter;
import com.meidusa.venus.registry.Register;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * client invoker调用代理类，附加处理校验、流控、降级相关切面操作
 * Created by Zhangzhihua on 2017/8/24.
 */
public class ClientInvokerProxy implements Invoker {

    private static Logger logger = LoggerFactory.getLogger(ClientInvokerProxy.class);

    /**
     * 异常处理
     */
    private VenusExceptionFactory venusExceptionFactory;

    /**
     * 认证配置
     */
    private DummyAuthenticator authenticator;

    /**
     * Athena上报服务
     */
    private AthenaDataService athenaDataService;

    /**
     * 静态配置地址
     */
    private ClientRemoteConfig remoteConfig;

    /**
     * 注册中心
     */
    private Register register;

    /**
     * injvm调用 TODO 初始化
     */
    private InjvmClientInvoker injvmInvoker = new InjvmClientInvoker();

    /**
     * 远程(包含同ip实例间)调用
     */
    private ClientRemoteInvoker clientRemoteInvoker = new ClientRemoteInvoker();

    private ClientMonitorFilter clientMonitorFilter;

    @Override
    public void init() throws RpcException {
    }

    @Override
    public Result invoke(Invocation invocation, URL url) throws RpcException {
        ClientInvocation clientInvocation = (ClientInvocation)invocation;
        try {
            //调用前切面处理，校验、流控、降级等
            for(Filter filter : getBeforeFilters()){
                Result result = filter.beforeInvoke(invocation,null);
                if(result != null){
                    VenusThreadContext.set(VenusThreadContext.RESPONSE_RESULT,result);
                    return result;
                }
            }

            //根据配置选择内部调用还是跨实例/远程调用
            if(isInjvmInvoke(clientInvocation)){
                Result result = getInjvmInvoker().invoke(invocation, url);
                VenusThreadContext.set(VenusThreadContext.RESPONSE_RESULT,result);
                return result;
            }else{
                Result result = getRemoteInvoker().invoke(invocation, url);
                VenusThreadContext.set(VenusThreadContext.RESPONSE_RESULT,result);
                return result;
            }
        } catch (Throwable e) {
            VenusThreadContext.set(VenusThreadContext.RESPONSE_EXCEPTION,e);
            //调用异常切面处理
            for(Filter filter : getThrowFilters()){
                Result result = filter.throwInvoke(invocation,url,e );
                if(result != null){
                    VenusThreadContext.set(VenusThreadContext.RESPONSE_RESULT,result);
                    return result;
                }
            }
            //TODO 本地异常情况
            throw  new RpcException(e);
        }finally {
            //调用结束切面处理
            for(Filter filter : getAfterFilters()){
                filter.afterInvoke(invocation,url);
            }
        }
    }

    /**
     * 判断是否invjm内部调用
     * @param invocation
     * @return
     */
    boolean isInjvmInvoke(ClientInvocation invocation){
        Service service = invocation.getService();
        Endpoint endpoint = invocation.getEndpoint();
        if (endpoint != null && service != null) {
            return !StringUtils.isEmpty(service.implement());
        }else{
            //本地调用
            //TODO 确认endpoint为空情况
            return true;
        }
    }

    public InjvmClientInvoker getInjvmInvoker() {
        return injvmInvoker;
    }

    /**
     * 获取remote Invoker
     * @return
     */
    public ClientRemoteInvoker getRemoteInvoker() {
        if(remoteConfig != null){
            clientRemoteInvoker.setRemoteConfig(remoteConfig);
        }
        if(register != null){
            clientRemoteInvoker.setRegister(register);
        }
        return clientRemoteInvoker;
    }

    /**
     * 获取前置filters TODO 初始化处理
     * @return
     */
    Filter[] getBeforeFilters(){
        return new Filter[]{
                //校验
                new ClientValidFilter(),
                //并发数流控
                new ClientActivesLimitFilter(),
                //TPS流控
                new ClientTpsLimitFilter(),
                //return降级
                new ClientReturnMockFilter(),
                //throw降级
                new ClientThrowMockFilter(),
                //mock降级
                new ClientCallbackMockFilter(),
                //athena监控
                new ClientAthenaMonitorFilter(),
                //venus监控
                getClientMonitorFilter()
        };
    }

    /**
     * 获取前置filters TODO 初始化处理
     * @return
     */
    Filter[] getThrowFilters(){
        return new Filter[]{
                //athena监控
                new ClientAthenaMonitorFilter(),
                //venus监控
                getClientMonitorFilter()
        };
    }

    /**
     * 获取after filters TODO 初始化处理
     * @return
     */
    Filter[] getAfterFilters(){
        return new Filter[]{
                //并发数流控
                new ClientActivesLimitFilter(),
                //athena监控
                new ClientAthenaMonitorFilter(),
                //venus监控
                getClientMonitorFilter()
        };
    }

    /**
     * getClientMonitorFilter
     * @return
     */
    ClientMonitorFilter getClientMonitorFilter(){
         if(clientMonitorFilter != null){
             return clientMonitorFilter;
         }
        clientMonitorFilter = new ClientMonitorFilter(getAthenaDataService());
        return clientMonitorFilter;
    }

    /**
     * 获取athena监控filter
     * @return
     */
    Filter getAthenaMonitorFilter(){
        try {
            //TODO 实例化
            Filter filter = (Filter) Class.forName("com.meidusa.venus.monitor.athena.filter.ClientAthenaMonitorFilter").newInstance();
            return filter;
        } catch (Exception e) {
            logger.error("new ClientAthenaMonitorFilter error.",e);
            return null;
        }
    }


    @Override
    public void destroy() throws RpcException {

    }

    public VenusExceptionFactory getVenusExceptionFactory() {
        return venusExceptionFactory;
    }

    public void setVenusExceptionFactory(VenusExceptionFactory venusExceptionFactory) {
        this.venusExceptionFactory = venusExceptionFactory;
    }

    public DummyAuthenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(DummyAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public ClientRemoteConfig getRemoteConfig() {
        return remoteConfig;
    }

    public void setRemoteConfig(ClientRemoteConfig remoteConfig) {
        this.remoteConfig = remoteConfig;
    }

    public Register getRegister() {
        return register;
    }

    public void setRegister(Register register) {
        this.register = register;
    }

    public AthenaDataService getAthenaDataService() {
        return athenaDataService;
    }

    public void setAthenaDataService(AthenaDataService athenaDataService) {
        this.athenaDataService = athenaDataService;
    }
}
