package com.meidusa.venus.support;

import com.meidusa.toolkit.common.util.StringUtil;
import com.meidusa.venus.Invocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * venus util
 * Created by Zhangzhihua on 2017/9/21.
 */
public class VenusUtil {

    private static Logger logger = LoggerFactory.getLogger(VenusUtil.class);

    /**
     * 获取服务路径
     * @param invocation
     * @return
     */
    public static String getServicePath(Invocation invocation){
        String servicePath = String.format(
                "%s/%s?version=%s",
                invocation.getServiceInterfaceName(),
                invocation.getServiceName(),
                invocation.getVersion()
        );
        return servicePath;
    }

    /**
     * 获取方法路径
     * @param invocation
     * @return
     */
    public static String getMethodPath(Invocation invocation){
        String methodPath = String.format(
                "%s/%s?version=%s&method=%s",
                invocation.getServiceInterfaceName(),
                invocation.getServiceName(),
                invocation.getVersion(),
                invocation.getMethodName()
        );
        return methodPath;
    }

    /**
     * 根据方法定义信息获取api名称
     * @param method
     * @param service
     * @param endpoint
     * @return
     */
    public static String getApiName(Method method, ServiceWrapper service, EndpointWrapper endpoint) {
        String serviceName = null;
        if (service == null || StringUtil.isEmpty(service.getName())) {
            serviceName = method.getDeclaringClass().getCanonicalName();
        } else {
            serviceName = service.getName();
        }

        String methodName = method.getName();
        if (endpoint == null || StringUtil.isEmpty(endpoint.getName())) {
            methodName = method.getName();
        } else {
            methodName = endpoint.getName();
        }

        return serviceName + "." + methodName;
    }

}