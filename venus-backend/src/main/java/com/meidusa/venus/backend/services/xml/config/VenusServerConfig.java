package com.meidusa.venus.backend.services.xml.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.meidusa.venus.backend.services.InterceptorMapping;

/**
 * venus服务端配置
 */
public class VenusServerConfig {

    private List<ServiceConfig> serviceConfigs = new ArrayList<ServiceConfig>();

    private Map<String, InterceptorMapping> interceptors = new HashMap<String, InterceptorMapping>();

    private Map<String, InterceptorStackConfig> interceptorStatcks = new HashMap<String, InterceptorStackConfig>();

    public void addService(ServiceConfig service) {
        serviceConfigs.add(service);
    }

    public void addInterceptor(InterceptorMapping mapping) {
        interceptors.put(mapping.getName(), mapping);
    }

    public InterceptorMapping getInterceptor(String name) {
        return interceptors.get(name);
    }

    public void addInterceptorStack(InterceptorStackConfig stack) {
        interceptorStatcks.put(stack.getName(), stack);
    }

    public List<ServiceConfig> getServiceConfigs() {
        return serviceConfigs;
    }

    public Map<String, InterceptorMapping> getInterceptors() {
        return interceptors;
    }

    public Map<String, InterceptorStackConfig> getInterceptorStatcks() {
        return interceptorStatcks;
    }

    public void setServiceConfigs(List<ServiceConfig> serviceConfigs) {
        this.serviceConfigs = serviceConfigs;
    }

    public void setInterceptors(Map<String, InterceptorMapping> interceptors) {
        this.interceptors = interceptors;
    }

    public void setInterceptorStatcks(Map<String, InterceptorStackConfig> interceptorStatcks) {
        this.interceptorStatcks = interceptorStatcks;
    }
}