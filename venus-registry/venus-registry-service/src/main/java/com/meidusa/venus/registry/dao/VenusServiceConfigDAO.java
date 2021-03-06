package com.meidusa.venus.registry.dao;

import java.util.List;

import com.meidusa.venus.registry.DAOException;
import com.meidusa.venus.registry.domain.VenusServiceConfigDO;

public interface VenusServiceConfigDAO {

	List<VenusServiceConfigDO> getServiceConfigs(Integer serviceId) throws DAOException;
	
	int getServiceConfigCount() throws DAOException;
	
	List<VenusServiceConfigDO> queryServiceConfigs(int pageSize, Integer id) throws DAOException;

}
