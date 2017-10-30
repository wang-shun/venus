package com.meidusa.venus.client.invoker;

import com.meidusa.venus.*;
import com.meidusa.venus.client.authenticate.DummyAuthenticator;
import com.meidusa.venus.client.factory.xml.config.ClientRemoteConfig;
import com.meidusa.venus.client.filter.limit.ClientActivesLimitFilter;
import com.meidusa.venus.client.filter.limit.ClientTpsLimitFilter;
import com.meidusa.venus.client.filter.mock.ClientCallbackMockFilter;
import com.meidusa.venus.client.filter.mock.ClientReturnMockFilter;
import com.meidusa.venus.client.filter.mock.ClientThrowMockFilter;
import com.meidusa.venus.client.filter.valid.ClientValidFilter;
import com.meidusa.venus.client.invoker.injvm.InjvmClientInvoker;
import com.meidusa.venus.exception.RpcException;
import com.meidusa.venus.exception.VenusExceptionFactory;
import com.meidusa.venus.io.utils.RpcIdUtil;
import com.meidusa.venus.monitor.VenusMonitorFactory;
import com.meidusa.venus.monitor.athena.filter.ClientAthenaMonitorFilter;
import com.meidusa.venus.monitor.filter.ClientMonitorFilter;
import com.meidusa.venus.registry.Register;
import com.meidusa.venus.support.EndpointWrapper;
import com.meidusa.venus.support.ServiceWrapper;
import com.meidusa.venus.support.VenusThreadContext;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
     * 静态配置地址
     */
    private ClientRemoteConfig remoteConfig;

    /**
     * 注册中心
     */
    private Register register;

    /**
     * injvm调用
     */
    private InjvmClientInvoker injvmInvoker = new InjvmClientInvoker();

    /**
     * 远程(包含同ip实例间)调用
     */
    private ClientRemoteInvoker clientRemoteInvoker = new ClientRemoteInvoker();

    private static boolean isEnableFilter = false;

    //前置filters
    private List<Filter> beforeFilters = new ArrayList<Filter>();
    //异常filters
    private List<Filter> throwFilters = new ArrayList<Filter>();
    //后置filters
    private List<Filter> afterFilters = new ArrayList<Filter>();

    //校验filter
    private ClientValidFilter clientValidFilter = new ClientValidFilter();
    //并发数流控
    private ClientActivesLimitFilter clientActivesLimitFilter = new ClientActivesLimitFilter();
    //TPS流控
    private ClientTpsLimitFilter clientTpsLimitFilter = new ClientTpsLimitFilter();
    //return降级
    private ClientReturnMockFilter clientReturnMockFilter = new ClientReturnMockFilter();
    //throw降级
    private ClientThrowMockFilter clientThrowMockFilter = new ClientThrowMockFilter();
    //mock降级
    private ClientCallbackMockFilter clientCallbackMockFilter = new ClientCallbackMockFilter();
    //athena监控
    private ClientAthenaMonitorFilter clientAthenaMonitorFilter = new ClientAthenaMonitorFilter();
    //venus监控上报filter
    private ClientMonitorFilter clientMonitorFilter = new ClientMonitorFilter();


    public ClientInvokerProxy(){
        init();
    }

    @Override
    public void init() throws RpcException {
        synchronized (this){
            if(isEnableFilter){
                initFilters();
            }
        }
    }

    @Override
    public Result invoke(Invocation invocation, URL url) throws RpcException {
        long bTime = System.currentTimeMillis();
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
                ClientRemoteInvoker clientRemoteInvoker = getRemoteInvoker();
                Result result = clientRemoteInvoker.invoke(invocation, url);
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
            if(logger.isWarnEnabled()){
                logger.warn("request rpcId:{} cost time:{}.", RpcIdUtil.getRpcId(clientInvocation.getClientId(),clientInvocation.getClientRequestId()),System.currentTimeMillis()-bTime);
            }
        }
    }

    /**
     * 判断是否invjm内部调用
     * @param invocation
     * @return
     */
    boolean isInjvmInvoke(ClientInvocation invocation){
        ServiceWrapper service = invocation.getService();
        EndpointWrapper endpoint = invocation.getEndpoint();
        if (endpoint != null && service != null) {
            return StringUtils.isNotEmpty(service.getImplement());
        }else{
            //本地调用
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
     * 初始化filters
     */
    void initFilters(){
        initBeforeFilters();
        initThrowFilters();
        initAfterFilters();
    }

    /**
     * 初始化前置切面
     */
    void initBeforeFilters(){
        beforeFilters.add(clientValidFilter);
        //流控
        beforeFilters.add(clientActivesLimitFilter);
        beforeFilters.add(clientTpsLimitFilter);
        //降级
        beforeFilters.add(clientReturnMockFilter);
        beforeFilters.add(clientThrowMockFilter);
        beforeFilters.add(clientCallbackMockFilter);
        //监控
        addMonitorFilters(beforeFilters);
    }

    /**
     * 初始化异常切面
     */
    void initThrowFilters(){
        //监控filters
        addMonitorFilters(throwFilters);
    }

    /**
     * 初始化后置切面
     */
    void initAfterFilters(){
        //流控
        afterFilters.add(clientActivesLimitFilter);
        //监控
        addMonitorFilters(afterFilters);
    }

    /**
     * 初始化监控filters
     */
    void addMonitorFilters(List<Filter> filterList){
        VenusMonitorFactory venusMonitorFactory = getVenusMonitorFactory();
        if(venusMonitorFactory != null){
            if(venusMonitorFactory.isEnableAthenaReport()){
                filterList.add(clientAthenaMonitorFilter);
            }
            if(venusMonitorFactory.isEnableVenusReport()){
                filterList.add(getClientMonitorFilter());
            }
        }
    }

    /**
     * 获取VenusMonitorFactory
     * @return
     */
    VenusMonitorFactory getVenusMonitorFactory(){
        return VenusMonitorFactory.getInstance();
    }

    public List<Filter> getBeforeFilters() {
        return beforeFilters;
    }

    public List<Filter> getThrowFilters() {
        return throwFilters;
    }

    public List<Filter> getAfterFilters() {
        return afterFilters;
    }


    public ClientMonitorFilter getClientMonitorFilter() {
        synchronized (clientMonitorFilter){
            if(!clientMonitorFilter.isRunning()){
                clientMonitorFilter.setAthenaDataService(getVenusMonitorFactory().getAthenaDataService());
                clientMonitorFilter.startProcessAndReporterTread();
            }
        }
        return clientMonitorFilter;
    }

    /**
     * 获取athena监控filter
     * @return
     */
    Filter getAthenaMonitorFilter(){
        try {
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

}

