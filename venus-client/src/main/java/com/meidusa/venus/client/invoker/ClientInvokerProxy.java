package com.meidusa.venus.client.invoker;

import com.meidusa.venus.*;
import com.meidusa.venus.client.ClientInvocation;
import com.meidusa.venus.client.factory.xml.config.ClientRemoteConfig;
import com.meidusa.venus.client.filter.limit.ClientActivesLimitFilter;
import com.meidusa.venus.client.filter.limit.ClientTpsLimitFilter;
import com.meidusa.venus.client.filter.mock.ClientCallbackMockFilter;
import com.meidusa.venus.client.filter.mock.ClientReturnMockFilter;
import com.meidusa.venus.client.filter.mock.ClientThrowMockFilter;
import com.meidusa.venus.client.filter.valid.ClientValidFilter;
import com.meidusa.venus.client.invoker.injvm.InjvmInvoker;
import com.meidusa.venus.client.invoker.venus.VenusClientInvoker;
import com.meidusa.venus.exception.RpcException;
import com.meidusa.venus.monitor.VenusMonitorFactory;
import com.meidusa.venus.monitor.athena.filter.ClientAthenaMonitorFilter;
import com.meidusa.venus.monitor.filter.ClientVenusMonitorFilter;
import com.meidusa.venus.registry.Register;
import com.meidusa.venus.support.VenusThreadContext;
import com.meidusa.venus.util.VenusLoggerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * client invoker调用代理类，附加处理校验、流控、降级相关切面操作
 * Created by Zhangzhihua on 2017/8/24.
 */
public class ClientInvokerProxy implements Invoker {

    private static Logger logger = VenusLoggerFactory.getDefaultLogger();

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
    private InjvmInvoker injvmInvoker = new InjvmInvoker();

    /**
     * 远程(包含同ip实例间)调用
     */
    private VenusClientInvoker venusInvoker = new VenusClientInvoker();

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
    //venus监控上报filter
    private static ClientVenusMonitorFilter clientVenusMonitorFilter = null;
    //athena监控
    private ClientAthenaMonitorFilter clientAthenaMonitorFilter = null;


    public ClientInvokerProxy(){
        init();
    }

    @Override
    public void init() throws RpcException {
        synchronized (this){
            VenusApplication venusApplication = VenusApplication.getInstance();
            if(venusApplication != null && venusApplication.isEnableFilter()){
                initFilters();
            }else{
                logger.warn("venus application is undefined or disabled filters,will disable filters.");
            }
        }
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

            //本地路由优先，根据配置选择内部调用还是跨实例/远程调用
            if(isInjvmInvoke(clientInvocation)){
                Result result = getInjvmInvoker().invoke(invocation, url);
                VenusThreadContext.set(VenusThreadContext.RESPONSE_RESULT,result);
                return result;
            }else{
                Result result = getVenusInvoker().invoke(invocation, url);
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
            throw e;
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
        /*
        ServiceWrapper service = invocation.getService();
        EndpointWrapper endpoint = invocation.getEndpoint();
        if (endpoint != null && service != null) {
            return StringUtils.isNotEmpty(service.getImplement());
        }else{
            //本地调用
            return true;
        }
        */
        //TODO 处理判断逻辑
        return false;
    }

    public InjvmInvoker getInjvmInvoker() {
        return injvmInvoker;
    }

    /**
     * 获取remote Invoker
     * @return
     */
    public VenusClientInvoker getVenusInvoker() {
        if(remoteConfig != null){
            venusInvoker.setRemoteConfig(remoteConfig);
        }
        if(register != null){
            venusInvoker.setRegister(register);
        }
        return venusInvoker;
    }

    /**
     * 初始化filters
     */
    void initFilters(){
        //初始化monitor filters
        initMonitorFilters();

        //添加filters
        addBeforeFilters();
        addThrowFilters();
        addAfterFilters();
    }

    /**
     * 初始化监控filters
     */
    void initMonitorFilters(){
        VenusMonitorFactory venusMonitorFactory = VenusMonitorFactory.getInstance();
        if(venusMonitorFactory != null){
            if(venusMonitorFactory.isEnableVenusReport()){
                if(clientVenusMonitorFilter == null){
                    try {
                        clientVenusMonitorFilter = new ClientVenusMonitorFilter();
                        clientVenusMonitorFilter.init();
                    } catch (Exception e) {
                        logger.warn("#########init venus monitor failed,will disabled venus report.",e);
                    }
                }
            }else{
                if(logger.isWarnEnabled()){
                    logger.warn("############not enable venus report,venus monitor filter diabled##############");
                }
            }

            if(venusMonitorFactory.isEnableAthenaReport()){
                try {
                    clientAthenaMonitorFilter = new ClientAthenaMonitorFilter();
                    clientAthenaMonitorFilter.init();
                } catch (Throwable e) {
                    logger.warn("##########init athena monitor failed,will disabled athena report.",e);
                }
            }else{
                if(logger.isWarnEnabled()){
                    logger.warn("############not enable athena report,athena monitor filter diabled##############");
                }
            }
        }else{
            if(logger.isWarnEnabled()){
                logger.warn("############not enable monitor report,vensu and athena monitor filter diabled##############");
            }
        }
    }

    /**
     * 初始化前置切面
     */
    void addBeforeFilters(){
        beforeFilters.add(clientValidFilter);
        //流控
        beforeFilters.add(clientActivesLimitFilter);
        beforeFilters.add(clientTpsLimitFilter);
        //降级
        beforeFilters.add(clientReturnMockFilter);
        beforeFilters.add(clientThrowMockFilter);
        beforeFilters.add(clientCallbackMockFilter);
        //监控
        if(clientVenusMonitorFilter != null){
            beforeFilters.add(clientVenusMonitorFilter);
        }
        if(clientAthenaMonitorFilter != null){
            beforeFilters.add(clientAthenaMonitorFilter);
        }
    }

    /**
     * 初始化异常切面
     */
    void addThrowFilters(){
        //监控filters
        if(clientVenusMonitorFilter != null){
            throwFilters.add(clientVenusMonitorFilter);
        }
        if(clientAthenaMonitorFilter != null){
            throwFilters.add(clientAthenaMonitorFilter);
        }
    }

    /**
     * 初始化后置切面
     */
    void addAfterFilters(){
        //流控
        afterFilters.add(clientActivesLimitFilter);
        //监控
        if(clientVenusMonitorFilter != null){
            afterFilters.add(clientVenusMonitorFilter);
        }
        if(clientAthenaMonitorFilter != null){
            afterFilters.add(clientAthenaMonitorFilter);
        }
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

    @Override
    public void destroy() throws RpcException {

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

