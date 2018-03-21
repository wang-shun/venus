package com.meidusa.venus.registry.dao;

import java.util.List;

import com.meidusa.venus.registry.DAOException;
import com.meidusa.venus.registry.domain.VenusServiceMappingDO;

public interface CacheVenusServiceMappingDAO {

	List<VenusServiceMappingDO> queryServiceMappings(int serviceId) throws DAOException;

}