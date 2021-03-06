package com.meidusa.venus.registry.dao;

import java.util.List;

import com.meidusa.venus.registry.DAOException;
import com.meidusa.venus.registry.data.move.OldServiceMappingDO;
import com.meidusa.venus.registry.data.move.ServiceMappingDTO;
import com.meidusa.venus.registry.domain.VenusServiceMappingDO;

public interface VenusServiceMappingDAO {

	boolean addServiceMapping(VenusServiceMappingDO venusServiceMappingDO) throws DAOException;

	boolean updateSubcribeServiceMapping(int id,int consumerAppId,boolean active, boolean isDelete) throws DAOException;
	
	boolean updateProviderServiceMapping(int id, boolean active, boolean isDelete,int providerAppId) throws DAOException;

	boolean updateHeartBeatTime(int serverId, String role) throws DAOException;

	boolean deleteServiceMapping(int id) throws DAOException;

	VenusServiceMappingDO getServiceMapping(Integer serverId, Integer serviceId, String role) throws DAOException;
	
	VenusServiceMappingDO getConsumerServiceMapping(Integer serverId, Integer serviceId, Integer consumerAppId) throws DAOException;

	List<VenusServiceMappingDO> getServiceMapping(Integer serviceId, String role, boolean isDelete) throws DAOException;

	VenusServiceMappingDO getServiceMapping(Integer id) throws DAOException;

	List<VenusServiceMappingDO> getServiceMappings(Integer serverId) throws DAOException;

	List<VenusServiceMappingDO> getServiceMappings(String dateStr,int second) throws DAOException;
	
	List<VenusServiceMappingDO> queryServiceMappings(int hour) throws DAOException;

	List<VenusServiceMappingDO> getDeleteServiceMappings(String updateTime, String role, boolean isDelete)
			throws DAOException;

	boolean updateServiceMappings(List<Integer> ids) throws DAOException;
	
	boolean deleteServiceMappings(List<Integer> ids) throws DAOException;
	
	boolean logicDeleteServiceMappings(List<Integer> ids) throws DAOException;
	
	List<VenusServiceMappingDO> getServiceMappings(int serviceId) throws DAOException;
	
	/**
	 * 根据 主机IP 端口 服务名 服务版号 判断 服务映射关系是否存在
	 * @param hostName
	 * @param port
	 * @param serviceName
	 * @param version
	 * @return
	 * @throws DAOException
	 */
	boolean existServiceMapping(String hostName, int port, String serviceName, String version) throws DAOException;
	
	/**
	 * 根据serverId查询在mapping表中的个数
	 * @param serverId
	 * @return
	 * @throws DAOException
	 */
	int getMappingCountByServerId(int serverId) throws DAOException;
	
	boolean updateHeartBeatTime(int serverId, List<Integer>serviceIds,String role) throws DAOException;
	
	List<ServiceMappingDTO> queryServiceMappings(List<String> serviceNames) throws DAOException ;
	
	List<ServiceMappingDTO> queryServiceMappings(String serviceName) throws DAOException ;
	
	int getMappingCount() throws DAOException;
	
	List<VenusServiceMappingDO> queryServiceMappings(Integer pageSize, Integer id) throws DAOException;
	
	boolean logicDeleteOldMappings(int serverId,int providerAppId,String role) throws DAOException;

	boolean updateHeartBeatTime(List<Integer> Ids) throws DAOException;

	List<Integer> queryMappingIds(int serverId, List<Integer> serviceIds, String role) throws DAOException;
	
	List<Integer> queryMappingIds(int serverId, List<Integer> serviceIds, int consumerAppId) throws DAOException;

}
