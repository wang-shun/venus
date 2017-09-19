package com.meidusa.venus.registry.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.meidusa.toolkit.common.util.ObjectUtil;

/**
 * 服务定义
 * 
 * @author structchen
 * 
 */
public class ServiceDefinitionDO {

	/**
	 * 服务名称
	 */
	private String name;
	
	/**
	 * 接口名称
	 */
	private String interfaceName;

	/**
	 * 服务版本范围
	 */
	private String versionRange;

	/**
	 * 是否激活状态
	 */
	private boolean active;

	/**
	 * 服务IP地址列表,默认格式: host:port
	 */
	private Set<String> ipAddress;

	private String description;

	/** 服务配置信息 */
	private List<VenusServiceConfigDO> serviceConfigs = new ArrayList<VenusServiceConfigDO>();

	public String getName() {
		return name;
	}

	public Set<String> getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(Set<String> ipAddress) {
		this.ipAddress = ipAddress;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getVersionRange() {
		return versionRange;
	}

	public void setVersionRange(String versionRange) {
		this.versionRange = versionRange;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<VenusServiceConfigDO> getServiceConfigs() {
		return serviceConfigs;
	}

	public void setServiceConfigs(List<VenusServiceConfigDO> serviceConfigs) {
		this.serviceConfigs = serviceConfigs;
	}

	public int hashCode() {
		int result = 114 + name.hashCode() + (versionRange == null ? 0 : versionRange.hashCode()) + (active ? 1 : 2);
		if (getIpAddress() == null) {
			return result;
		} else {
			for (String ip : getIpAddress()) {
				result += ip.hashCode();
			}
			return result;
		}
	}

	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj instanceof ServiceDefinitionDO) {
			ServiceDefinitionDO target = (ServiceDefinitionDO) obj;
			boolean result = StringUtils.equals(name, target.getName());
			result = result && StringUtils.equals(versionRange, target.getVersionRange());
			result = result && this.getIpAddress() != null && target.getIpAddress().equals(this.getIpAddress());
			result = result && ObjectUtil.equals(active, target.isActive());
			return result;
		}

		return false;
	}

	public String getInterfaceName() {
		return interfaceName;
	}

	public void setInterfaceName(String interfaceName) {
		this.interfaceName = interfaceName;
	}

	@Override
	public String toString() {
		return "ServiceDefinitionDO [name=" + name + ", interfaceName=" + interfaceName + ", versionRange=" + versionRange
				+ ", active=" + active + ", ipAddress=" + ipAddress + ", description=" + description
				+ ", serviceConfigs=" + serviceConfigs + "]";
	}


}