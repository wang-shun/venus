/*
 * Copyright 2008-2108 amoeba.meidusa.com 
 * 
 * 	This program is free software; you can redistribute it and/or modify it under the terms of 
 * the GNU AFFERO GENERAL PUBLIC LICENSE as published by the Free Software Foundation; either version 3 of the License, 
 * or (at your option) any later version. 
 * 
 * 	This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU AFFERO GENERAL PUBLIC LICENSE for more details. 
 * 	You should have received a copy of the GNU AFFERO GENERAL PUBLIC LICENSE along with this program; 
 * if not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.meidusa.venus.client.factory.xml.config;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务引用配置
 */
@XStreamAlias("service")
public class ReferenceService {

    @XStreamAsAttribute
    private String type;

    private Class<?> clzType;

    private String beanName;

    private Object instance;

    private int version;

    //远程配置名称
    private String remote;

    //ip地址列表
    @XStreamAsAttribute
    private String ipAddressList;

    //连接数
    @XStreamAsAttribute
    private int coreConnections;

    //超时时间
    @XStreamAsAttribute
    private int timeout;

    //重试次数
    @XStreamAsAttribute
    private int retries;

    //集群容错策略
    @XStreamAsAttribute
    private String cluster;

    //负载均衡策略
    @XStreamAsAttribute
    private String loadbalance;

    @XStreamImplicit
    private List<ReferenceMethod> methodList = new ArrayList<ReferenceMethod>();

    private Map<String, ReferenceServiceConfig> endPointMap = new HashMap<String, ReferenceServiceConfig>();
    private int timeWait;
    private boolean enabled = true;
    
    public int getTimeWait() {
        return timeWait;
    }

    public void setTimeWait(int soTimeout) {
        this.timeWait = soTimeout;
    }

    public String getIpAddressList() {
        return ipAddressList;
    }

    public void setIpAddressList(String ipAddressList) {
        this.ipAddressList = ipAddressList;
    }

    public String getRemote() {
        return remote;
    }

    public void setRemote(String remote) {
        this.remote = remote;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Class<?> getClzType() {
        return clzType;
    }

    public void setClzType(Class<?> clzType) {
        this.clzType = clzType;
    }

    public Object getInstance() {
        return instance;
    }

    public void setInstance(Object implement) {
        this.instance = implement;
    }

    public void addEndPointConfig(ReferenceServiceConfig config) {
        endPointMap.put(config.getName(), config);
    }

    public ReferenceServiceConfig getEndpointConfig(String key) {
        return endPointMap.get(key);
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getLoadbalance() {
        return loadbalance;
    }

    public void setLoadbalance(String loadbalance) {
        this.loadbalance = loadbalance;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getCoreConnections() {
        return coreConnections;
    }

    public void setCoreConnections(int coreConnections) {
        this.coreConnections = coreConnections;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<ReferenceMethod> getMethodList() {
        return methodList;
    }

    public void setMethodList(List<ReferenceMethod> methodList) {
        this.methodList = methodList;
    }
}