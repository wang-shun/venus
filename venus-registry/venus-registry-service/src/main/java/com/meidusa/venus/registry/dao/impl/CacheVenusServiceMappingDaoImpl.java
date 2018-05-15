package com.meidusa.venus.registry.dao.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.CollectionUtils;

import com.meidusa.toolkit.common.runtime.GlobalScheduler;
import com.meidusa.venus.registry.DAOException;
import com.meidusa.venus.registry.LogUtils;
import com.meidusa.venus.registry.dao.CacheVenusServiceMappingDAO;
import com.meidusa.venus.registry.dao.VenusServiceMappingDAO;
import com.meidusa.venus.registry.domain.VenusServiceMappingDO;
import com.meidusa.venus.registry.domain.RegisteConstant;

public class CacheVenusServiceMappingDaoImpl implements CacheVenusServiceMappingDAO {

	private VenusServiceMappingDAO venusServiceMappingDAO;

	private Map<Integer, List<VenusServiceMappingDO>> cacheServiceMappingMap = new HashMap<Integer, List<VenusServiceMappingDO>>();
	
	private Map<Integer, List<VenusServiceMappingDO>> cacheServerMappingMap = new HashMap<Integer, List<VenusServiceMappingDO>>();

	private static final int PAGE_SIZE_200 = 200;

	private volatile boolean loacCacheRunning = false;

	public VenusServiceMappingDAO getVenusServiceMappingDAO() {
		return venusServiceMappingDAO;
	}

	public void setVenusServiceMappingDAO(VenusServiceMappingDAO venusServiceMappingDAO) {
		this.venusServiceMappingDAO = venusServiceMappingDAO;
	}

	public void init() {
		GlobalScheduler.getInstance().scheduleAtFixedRate(new LoadCacheServicesMappingRunnable(), 1, 20,
				TimeUnit.SECONDS);
	}

	public void load() {
/*		loacCacheRunning = true;
		if (loacCacheRunning) {
			cacheServiceMappingMap.clear();
		}*/
		List<VenusServiceMappingDO> allServices=new ArrayList<VenusServiceMappingDO>();
		Integer totalCount = venusServiceMappingDAO.getMappingCount();
		if (null != totalCount && totalCount > 0) {
			int mod = totalCount % PAGE_SIZE_200;
			int count = totalCount / PAGE_SIZE_200;
			if (mod > 0) {
				count = count + 1;
			}
			int id = 0;
			for (int i = 0; i < count; i++) {
				List<VenusServiceMappingDO> services = venusServiceMappingDAO.queryServiceMappings(PAGE_SIZE_200, id);
				if (CollectionUtils.isNotEmpty(services)) {
					id = services.get(services.size() - 1).getId();
					allServices.addAll(services);
				}
			}
		}
		
		if (CollectionUtils.isNotEmpty(allServices)) {
			loacCacheRunning = true;
			cacheServiceMappingMap.clear();
			cacheServerMappingMap.clear();
			for (Iterator<VenusServiceMappingDO> iterator = allServices.iterator(); iterator.hasNext();) {
				VenusServiceMappingDO vs = iterator.next();
				if (null != vs.getIsDelete() && vs.getIsDelete() == false
						&& vs.getRole().equals(RegisteConstant.PROVIDER)) {
					putToMap(vs.getServiceId(), vs);
				}
				putToServerMap(vs.getServerId(),vs);
			}
			loacCacheRunning = false;
		}
	}

	private void putToMap(Integer key, VenusServiceMappingDO vs) {
		List<VenusServiceMappingDO> list = cacheServiceMappingMap.get(key);
		if (null == list) {
			list = new ArrayList<VenusServiceMappingDO>();
			list.add(vs);
			cacheServiceMappingMap.put(key, list);
		} else {
			if (!list.contains(vs)) {
				list.add(vs);
			}
		}
	}
	
	private void putToServerMap(Integer serverId, VenusServiceMappingDO vs) {
		List<VenusServiceMappingDO> list = cacheServerMappingMap.get(serverId);
		if (null == list) {
			list = new ArrayList<VenusServiceMappingDO>();
			list.add(vs);
			cacheServerMappingMap.put(serverId, list);
		} else {
			if (!list.contains(vs)) {
				list.add(vs);
			}
		}
	}

	private class LoadCacheServicesMappingRunnable implements Runnable {

		@Override
		public void run() {
			try {
				long start = System.currentTimeMillis();
				load();
				long end = System.currentTimeMillis();
				long consumerTime = end - start;
				LogUtils.logCacheSlow(consumerTime, "LoadCacheServicesMappingRunnable load() ");
				LogUtils.DEFAULT_LOG.info(
						"LoadCacheServicesMappingRunnable start=>{}, end=>{},consumerTime=>{},cacheServiceMappingMap size=>{}",
						start, end, consumerTime, cacheServiceMappingMap.size());
			} catch (Exception e) {
				LogUtils.ERROR_LOG.error("load service mapping cache data error", e);
			} finally {
				loacCacheRunning = false;
			}
		}

	}

	public List<VenusServiceMappingDO> queryServiceMappings(int serviceId) throws DAOException {
		if (loacCacheRunning) {
			return null;
		}
		if (serviceId > 0) {
			return cacheServiceMappingMap.get(serviceId);
		}
		return null;
	}
	
	public List<Integer> queryServiceMappingIds(int serverId, List<Integer> serviceIds, String role)
			throws DAOException {
		if (loacCacheRunning) {
			return null;
		}
		List<Integer> returnList = new ArrayList<Integer>();
		if (serverId > 0) {
			List<VenusServiceMappingDO> list = cacheServerMappingMap.get(serverId);
			if (CollectionUtils.isNotEmpty(list)) {
				for (Iterator<VenusServiceMappingDO> iterator = list.iterator(); iterator.hasNext();) {
					VenusServiceMappingDO vs = iterator.next();
					for (Iterator<Integer> it = serviceIds.iterator(); it.hasNext();) {
						Integer serviceId = it.next();
						if (null != vs.getServiceId() && vs.getServiceId().intValue() == serviceId.intValue()
								&& vs.getRole().equals(role)) {
							returnList.add(vs.getId());
						}
					}
				}
			}
		}
		return returnList;
	}

}
