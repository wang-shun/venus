package com.meidusa.venus.registry.mysql;

import com.caucho.hessian.client.HessianProxyFactory;
import com.meidusa.fastjson.JSON;
import com.meidusa.fastjson.TypeReference;
import com.meidusa.toolkit.common.runtime.GlobalScheduler;
import com.meidusa.venus.ConnectionFactory;
import com.meidusa.venus.URL;
import com.meidusa.venus.registry.Register;
import com.meidusa.venus.registry.VenusRegisteException;
import com.meidusa.venus.registry.domain.RegisteConstant;
import com.meidusa.venus.registry.domain.VenusServiceDefinitionDO;
import com.meidusa.venus.registry.service.RegisterService;
import com.meidusa.venus.registry.util.RegistryUtil;
import com.meidusa.venus.support.MonitorResourceFacade;
import com.meidusa.venus.support.VenusConstants;
import com.meidusa.venus.support.VenusContext;
import com.meidusa.venus.util.VenusLoggerFactory;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;

/**
 * mysql服务注册中心类 Created by Zhangzhihua on 2017/7/27.
 */
public class MysqlRegister implements Register {

	private static Logger logger = VenusLoggerFactory.getDefaultLogger();

	private static Logger exceptionLogger = VenusLoggerFactory.getExceptionLogger();

	/** 已注册成功的URL */
	protected Set<URL> registeUrls = new CopyOnWriteArraySet<URL>();

	/** 已订阅成功的URL */
	protected Set<URL> subscribleUrls = new CopyOnWriteArraySet<URL>();

	/** 注册失败的URLS */
	private Set<URL> registeFailUrls = new HashSet<URL>();// 失败的继续跑启线程定时运行

	/** 订阅失败的URLS */
	private Set<URL> subscribleFailUrls = new HashSet<URL>();// 失败的继续跑启线程定时运行

	/** 已订阅成功的 服务定义对象 */
	private ConcurrentMap<String, List<VenusServiceDefinitionDO>> subscribleServiceDefinitionMap = new ConcurrentHashMap<String, List<VenusServiceDefinitionDO>>();

	private boolean loadRunning = false;

	private boolean heartbeatRunning = false;

	private RegisterService registerService = null;

	private String fileCachePath ;

	//是否开启本地文件缓存
	private static boolean isEnableFileCache = true;
	
	private static final int PAGE_SIZE = 50;

	public MysqlRegister(RegisterService registerService) {
		this.registerService = registerService;
		try {
			init();
		} catch (Exception e) {
			throw new VenusRegisteException(e);
		}
	}

	/**
	 * 初始化
	 */
	void init() {
		if (isWindows()) {
			String property = System.getProperty("user.home");
			fileCachePath = property + File.separator + "venus" + File.separator
					+ ".venusCache.txt";
		} else {
			String property = System.getProperty("user.home");
			if (property.startsWith(File.separator)) {
				fileCachePath = property + File.separator + "venus" + File.separator + ".venusCache.txt";
			} else {
				fileCachePath = File.separator + property + File.separator + "venus" + File.separator
						+ ".venusCache.txt";
			}
		}
		if(logger.isInfoEnabled()){
			logger.info("venusCachePath:{}",fileCachePath);
		}
		if (!loadRunning) {
			GlobalScheduler.getInstance().scheduleAtFixedRate(new UrlFailRetryRunnable(), 10, VenusConstants.FAIL_RETRY_INTERVAL, TimeUnit.SECONDS);
			GlobalScheduler.getInstance().scheduleAtFixedRate(new ServiceDefLoaderRunnable(), 10, VenusConstants.SERVER_DEFINE_LOAD_INTERVAL, TimeUnit.SECONDS);
			loadRunning = true;
		}
	}

	@Override
	public void registe(URL url) throws VenusRegisteException {
		try {
			if(logger.isInfoEnabled()){
				logger.info("registe service:{}.",url);
			}
			registerService.registe(url);
			heartbeat();
		} catch (Exception e) {
			registeFailUrls.add(url);
			throw new VenusRegisteException("registe service failed:" + getServiceName(url), e);
		}
		registeUrls.add(url);

	}

	@Override
	public void unregiste(URL url) throws VenusRegisteException {
		if(logger.isInfoEnabled()){
			logger.info("unregiste service:{}.",url);
		}
		try {
			boolean unregiste = registerService.unregiste(url);
			if (unregiste) {
				registeUrls.remove(url);
			}
		} catch (Exception e) {
			throw new VenusRegisteException("unregiste service failed:" + getServiceName(url), e);
		}
	}

	@Override
	public boolean subscrible(URL url) throws VenusRegisteException {
		long bTime = System.currentTimeMillis();
		if(logger.isInfoEnabled()){
			logger.info("subscrible service:{}.", url);
		}
		boolean success = true;
		try {
			registerService.subscrible(url);
			heartbeat();
		} catch (Exception e) {
			subscribleFailUrls.add(url);
			exceptionLogger.error("subscrible service failed:" + getServiceName(url), e);
			success = false;
		}
		subscribleUrls.add(url);
		return success;
	}

	@Override
	public void unsubscrible(URL url) throws VenusRegisteException {
		if(logger.isInfoEnabled()){
			logger.info("unsubscrible service:{}.",url);
		}
		try {
			boolean unsubscrible = registerService.unsubscrible(url);
			if (unsubscrible) {
				subscribleUrls.remove(url);
			}
		} catch (Exception e) {
			throw new VenusRegisteException("unsubscrible service failed:" + getServiceName(url), e);
		}
	}

	@Override
	public void heartbeat() throws VenusRegisteException {
		if (!heartbeatRunning) {
			GlobalScheduler.getInstance().scheduleAtFixedRate(new HeartBeatRunnable(), 3, VenusConstants.HEARTBEAT_INTERVAL,
					TimeUnit.SECONDS);
			heartbeatRunning = true;
		}
	}

	@Override
	public List<VenusServiceDefinitionDO> lookup(URL url) throws VenusRegisteException {
		// 接口名 服务名 版本号 加载服务的server信息及serviceConfig信息
		// 根据本地 VenusServiceDefinitionDO 列表去查找
		String key = RegistryUtil.getKeyFromUrl(url);
		List<VenusServiceDefinitionDO> serviceDefinitions = subscribleServiceDefinitionMap.get(key);
		if (CollectionUtils.isEmpty(serviceDefinitions)) {
			if(isEnableFileCache()){
				serviceDefinitions = findSrvDefListFromFileCache(key);
			}
		}
		return serviceDefinitions;
	}


	@Override
	public List<VenusServiceDefinitionDO> lookup(URL url, boolean isQueryFromRegister) throws VenusRegisteException {
		if (isQueryFromRegister) {
			return registerService.findServiceDefinitions(url);
		} else {
			return lookup(url);
		}
	}
	
	private Map<String, List<VenusServiceDefinitionDO>> batchQueryServiceDefines(List<URL> urlList) {
		Map<String, List<VenusServiceDefinitionDO>> returnMap = new HashMap<String, List<VenusServiceDefinitionDO>>();
		if (urlList.size() <= 0) {
			return returnMap;
		}
		int totalCount = urlList.size();
		int mod = totalCount % PAGE_SIZE;
		int count = totalCount / PAGE_SIZE;
		if (mod > 0) {
			count = count + 1;
		}
		for (int i = 0; i < count; i++) {// 分批查询，减少http请求次数
			int end = (i + 1) * PAGE_SIZE;
			if ((i + 1) * PAGE_SIZE >= totalCount) {
				end = totalCount;
			}
			int start = i * PAGE_SIZE;
			List<URL> subList = urlList.subList(start, end);
			Map<String, List<VenusServiceDefinitionDO>> queryServiceDefinitionsMap = registerService
					.queryServiceDefinitions(subList);
			returnMap.putAll(queryServiceDefinitionsMap);
		}
		return returnMap;
	}


	@Override
	public void load() throws VenusRegisteException {
		if (CollectionUtils.isNotEmpty(subscribleUrls)) {
			Map<String, List<VenusServiceDefinitionDO>> localDefinitionMap = new HashMap<String, List<VenusServiceDefinitionDO>>();
			boolean hasException=false;
			List<URL> copySubscribleUrls = this.copyUrlToList(subscribleUrls);
			filteProperties(copySubscribleUrls);
			
			try {
				localDefinitionMap  = batchQueryServiceDefines(copySubscribleUrls);
				for (URL url : subscribleUrls) {
					String servicePath = RegistryUtil.getKeyFromUrl(url);
					List<VenusServiceDefinitionDO> serviceDefinitions = localDefinitionMap.get(servicePath);
					if (CollectionUtils.isNotEmpty(serviceDefinitions)) {
						subscribleServiceDefinitionMap.put(servicePath, serviceDefinitions);

						//初始化或更新服务连接
						VenusContext.getInstance().getConnectionProcesser().put(servicePath,getServiceAddressList(serviceDefinitions));
					} else {
						subscribleServiceDefinitionMap.remove(servicePath);

						//删除服务及连接资源
						VenusContext.getInstance().getConnectionProcesser().remove(servicePath);
					}
				}
			} catch (Exception e) {
				hasException = true;
				exceptionLogger.error("load service definition failed.", e);
			}
			
			// 查询有异常或数据为空 不写本地缓存
			if (hasException || MapUtils.isEmpty(localDefinitionMap)) {
				return;
			}

			//若开启本地文件缓存，则写文件
			if (isEnableFileCache()) {
				saveSrvDefListToFileCache(localDefinitionMap);
			}

		}
	}

	/**
	 * 获取服务地址列表
	 * @param serviceDefinitionDOList
	 * @return
	 */
	List<String> getServiceAddressList(List<VenusServiceDefinitionDO> serviceDefinitionDOList){
		List<String> list = new ArrayList<>();
		for(VenusServiceDefinitionDO srvDef:serviceDefinitionDOList){
			Set<String> sets = srvDef.getIpAddress();
			for(String item:sets){
				list.add(item);
			}
		}
		return list;
	}

	/**
	 * 是否开启本地文件缓存
	 * @return
	 */
	boolean isEnableFileCache(){
		return isEnableFileCache;
	}


	public Set<URL> getRegisteUrls() {
		return registeUrls;
	}

	public Set<URL> getSubscribleUrls() {
		return subscribleUrls;
	}

	/**
	 * 保存服务定义清单到本地文件缓存
	 * @param jsons
	 */
	void saveSrvDefListToFileCache(Map<String, List<VenusServiceDefinitionDO>>  jsons){
		writeFile(fileCachePath, jsons);
	}

	/**
	 * 从本地文件缓存查找服务定义信息
	 * @param key
	 * @return
	 */
	List<VenusServiceDefinitionDO> findSrvDefListFromFileCache(String key){
		Map<String, List<VenusServiceDefinitionDO>> map = readFile(fileCachePath);//new HashMap<String, List<VenusServiceDefinitionDO>>();
		List<VenusServiceDefinitionDO> serviceDefinitions = map.get(key);
		return serviceDefinitions;
	}

	@Override
	public void destroy() throws VenusRegisteException {
		if (CollectionUtils.isNotEmpty(registeUrls)) {
			for (URL url : registeUrls) {
				unregiste(url);
			}
		}
		if (CollectionUtils.isNotEmpty(subscribleUrls)) {
			for (URL url : subscribleUrls) {
				unsubscrible(url);
			}

			//刷新订阅信息
			load();
		}
		registeUrls.clear();
		subscribleUrls.clear();
		registeFailUrls.clear();
		subscribleFailUrls.clear();
		subscribleServiceDefinitionMap.clear();
	}
	
	private List<URL> copyUrlToList(Set<URL> sourceUrls){
		if(CollectionUtils.isEmpty(sourceUrls)){
			return new ArrayList<URL>();
		}

		List<URL> targetUrls = new CopyOnWriteArrayList<URL>();
		try {
			for(URL sourceUrl:sourceUrls){
                URL targetUrl = new URL();
                BeanUtils.copyProperties(targetUrl,sourceUrl);
                targetUrls.add(targetUrl);
            }
		} catch (IllegalAccessException e) {
			return new ArrayList<URL>();
		} catch (InvocationTargetException e) {
			return new ArrayList<URL>();
		}
		return targetUrls;
	}
	
	
	/**
	 * 过滤不需要的属性
	 * @param urls
	 */
	void filteProperties(Collection<URL> urls){
		if(CollectionUtils.isEmpty(urls)){
			return;
		}
		for(URL url:urls){
			url.setMethods(null);
			url.setProperties(null);
			url.setRemoteConfig(null);
			url.setServiceDefinition(null);
		}
	}

	/**
	 * 注册订阅失败重试任务
	 */
	private class UrlFailRetryRunnable implements Runnable {
		@Override
		public void run() {
			if (CollectionUtils.isNotEmpty(registeFailUrls)) {
				for (Iterator<URL> iterator = registeFailUrls.iterator(); iterator.hasNext();) {
					URL url = iterator.next();
					try {
						registe(url);
						iterator.remove();
					} catch (Exception e) {
						String name = getServiceName(url);
						String version = "";
						if (StringUtils.isNotBlank(url.getVersion()) && !"null".equals(url.getVersion())) {
							version = url.getVersion();
						}
						String errorMsg = String.format("registe retry failed,service:%s,version:%s.",name,version);
						exceptionLogger.error(errorMsg,e);
					}
				}
			}
			if (CollectionUtils.isNotEmpty(subscribleFailUrls)) {
				for (Iterator<URL> iterator = subscribleFailUrls.iterator(); iterator.hasNext();) {
					URL url = iterator.next();
					try {
						boolean subscrible = subscrible(url);
						if (subscrible) {
							iterator.remove();
						}
					} catch (Exception e) {
						String name = getServiceName(url);
						String version = "";
						if (StringUtils.isNotBlank(url.getVersion()) && !"null".equals(url.getVersion())) {
							version = url.getVersion();
						}
						String errorMsg = String.format("subscrible retry failed,service:%s,version:%s.",name,version);
						exceptionLogger.error(errorMsg,e);
					}
				}
			}

		}


	}

	/**
	 * 服务定义加载任务
	 */
	private class ServiceDefLoaderRunnable implements Runnable {
		public void run() {
			try {
				load();
			} catch (VenusRegisteException e) {
				if(exceptionLogger.isErrorEnabled()){
					exceptionLogger.error("load services def failed.", e);
				}
			}
		}
	}

	/**
	 * 心跳上报任务
	 */
	private class HeartBeatRunnable implements Runnable {
		@Override
		public void run() {
			Map<String, Set<URL>> maps = new HashMap<String, Set<URL>>();

			Set<URL> copyRegisteUrls = copyUrls(registeUrls);
			filteProperties(copyRegisteUrls);
			Set<URL> copySubscribleUrls = copyUrls(subscribleUrls);
			filteProperties(copySubscribleUrls);

			maps.put(RegisteConstant.PROVIDER, copyRegisteUrls);
			maps.put(RegisteConstant.CONSUMER, copySubscribleUrls);
			try {
				registerService.heartbeat(maps);

				//添加到监控项中
				MonitorResourceFacade.getInstance().addProperty("registeUrls",copyRegisteUrls);
				MonitorResourceFacade.getInstance().addProperty("subscribleUrls",copySubscribleUrls);
			} catch (Exception e) {
				exceptionLogger.error("heartbeat report failed.", e);
			}
		}

		/**
		 * 复制url列表，目的过滤心跳上报不需要的属性
		 * @param sourceUrls
		 * @return
		 */
		Set<URL> copyUrls(Set<URL> sourceUrls){
			if(CollectionUtils.isEmpty(sourceUrls)){
				return sourceUrls;
			}
			
			Set<URL> targetUrls = new CopyOnWriteArraySet<URL>();
			try {
				for(URL sourceUrl:sourceUrls){
					URL targetUrl = new URL();
					BeanUtils.copyProperties(targetUrl,sourceUrl);
					targetUrls.add(targetUrl);
				}
			} catch (IllegalAccessException e) {
				return sourceUrls;
			} catch (InvocationTargetException e) {
				return sourceUrls;
			}
			return targetUrls;
		}

	}


	private static String getServiceName(URL url) {
		String name = "";
		if (StringUtils.isNotBlank(url.getServiceName()) && !"null".equals(url.getServiceName())) {
			name = url.getServiceName();
		} else {
			name = url.getInterfaceName();
		}
		return name;
	}
	
	public static Map<String, List<VenusServiceDefinitionDO>> readFile(String filePath) {
		if (!filePath.endsWith(".txt")) {
			return null;
		}
		File file = new File(filePath);
		Map<String, List<VenusServiceDefinitionDO>> parseMap = new HashMap<String, List<VenusServiceDefinitionDO>>();
		if (file.exists()) {
			RandomAccessFile randomAccessFile = null;
			try {
				randomAccessFile = new RandomAccessFile(file, "r");
			} catch (FileNotFoundException e) {
				exceptionLogger.error("readFile filePath=>" + filePath + " is error", e);
			}
			String str = null;
			try {
				while ((str = randomAccessFile.readLine()) != null) {
					String e = new String(str.getBytes("ISO-8859-1"),"UTF-8");
					parseMap = JSON.parseObject(e, new TypeReference<Map<String, List<VenusServiceDefinitionDO>>>(){});
				}
			} catch (IOException e) {
				exceptionLogger.error("readFile filePath=>" + filePath + " is error", e);
			} catch (Exception e) {//解析出错，清空内容,解决缓存内容 老的格式与新格式不兼容 问题
				clearCacheContent(filePath);
			}finally {
				if (null != randomAccessFile) {
					try {
						randomAccessFile.close();
					} catch (IOException e) {
						// ingore
					}
				}
			}
		}
		return parseMap;
	}

	private static void clearCacheContent(String filePath) {
		RandomAccessFile ra = null;
		FileChannel fc = null;
		try {
			File f = new File(filePath);
			if (f.isFile()) {
				ra = new RandomAccessFile(f, "rw");
				fc = ra.getChannel();
				fc.truncate(0);
			}
		} catch (Exception ee) {
			exceptionLogger.error("clearCacheContent readFile filePath=>" + filePath + " is error", ee);
		} finally {
			if (null != fc) {
				try {
					fc.close();
				} catch (Exception eee) {
					// ingore
				}
			}
			if (null != ra) {
				try {
					ra.close();
				} catch (Exception eee) {
					// ingore
				}
			}
		}
	}

	private static void mkDir(File file) {
		if (file.getParentFile().exists()) {
			file.mkdir();
		} else {
			mkDir(file.getParentFile());
			file.mkdir();
		}
	}

	/**
	 * 写文件(文件目录必须存在)
	 * 
	 * @param filePath
	 */
	public static void writeFile(String filePath, Map<String, List<VenusServiceDefinitionDO>>  jsonMap) {
		if (filePath.endsWith(".txt")) {
			String folderPath = filePath.substring(0, filePath.lastIndexOf(File.separator));
			File f = new File(folderPath);
			if (!f.exists()) {
				f.mkdirs();
			}
		} else {
			return;
		}
		if (MapUtils.isEmpty(jsonMap)) {
			File f = new File(filePath);
			try {
				if(f.exists()){
					f.delete();
				}
				if (f.createNewFile()) {
					f.setExecutable(true);
					f.setReadable(true);
					f.setWritable(true);
				}
			} catch (IOException e) {
				//ingore
			}
			return;
		}
		if (MapUtils.isEmpty(jsonMap)) {
			return;
		}
		RandomAccessFile randomAccessFile =null;
		FileChannel fileChannel = null;
		try {
			File file = new File(filePath);
			if (file.createNewFile()) {
				file.setExecutable(true);
				file.setReadable(true);
				file.setWritable(true);
			}
			if (file.isFile()) {
				randomAccessFile = new RandomAccessFile(file, "rw");
				fileChannel =  randomAccessFile.getChannel();
				fileChannel.truncate(0);
				randomAccessFile.write(JSON.toJSONString(jsonMap).getBytes("UTF-8"));
				/*
				for (String json : needWriteList) {
					randomAccessFile.write(json.getBytes("UTF-8"));
					randomAccessFile.write("\n".getBytes("UTF-8"));
				}
				*/
				randomAccessFile.close();
			}
		} catch (IOException e) {
			exceptionLogger.error("writeFile filePath=>" + filePath + " is error", e);
		} catch (NullPointerException e) {
			exceptionLogger.error("writeFile filePath=>" + filePath + " is error", e);
		} finally {
			if (null != randomAccessFile) {
				try {
					randomAccessFile.close();
				} catch (IOException e) {
				}
			}
			if(fileChannel != null){
				try {
					fileChannel.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private static List<String> getWriteList(List<String> oldList, List<String> newList) {
		if (CollectionUtils.isEmpty(oldList)) {
			return newList;
		}
		List<String> returnList = new ArrayList<String>();
		for (Iterator<String> iterator = oldList.iterator(); iterator.hasNext();) {
			String json = iterator.next();
			List<VenusServiceDefinitionDO> oldObject = JSON.parseArray(json, VenusServiceDefinitionDO.class);
			if (CollectionUtils.isNotEmpty(newList)) {
				for (String str : newList) {
					List<VenusServiceDefinitionDO> newObject = JSON.parseArray(str, VenusServiceDefinitionDO.class);
					if (RegistryUtil.getKey(oldObject.get(0)).equals(RegistryUtil.getKey(newObject.get(0)))) {
						iterator.remove();
					}
				}
			}
		}
		returnList.addAll(oldList);
		returnList.addAll(newList);
		return returnList;

	}

	public static boolean isWindows() {
		String os = System.getProperty("os.name");
		return os.toLowerCase().startsWith("win");
	}

	
	/*public static void main(String args[]) {
		Map<String, List<VenusServiceDefinitionDO>> readFileJsons = readFile("D:\\Users\\longhaisheng\\venus\\old_cache.txt");
		Map<String, List<VenusServiceDefinitionDO>> map = new HashMap<String, List<VenusServiceDefinitionDO>>();
		if (CollectionUtils.isNotEmpty(readFileJsons)) {
			for (String str : readFileJsons) {
				List<VenusServiceDefinitionDO> parseObject = JSON.parseArray(str, VenusServiceDefinitionDO.class);
				if (CollectionUtils.isNotEmpty(parseObject)) {
					map.put(getKey(parseObject.get(0)), parseObject);
				}
			}
	}*/
	
	/*public static void main(String args[]) {
		VenusServiceDefinitionDO def1 = new VenusServiceDefinitionDO();
		VenusServiceDefinitionDO def2 = new VenusServiceDefinitionDO();

		RouterRule rr = new RouterRule();
		VenusServiceConfigDO conf = new VenusServiceConfigDO();
		conf.setRouterRule(rr);
		
		List<VenusServiceConfigDO> serviceConfigs = new ArrayList<VenusServiceConfigDO>();
		serviceConfigs.add(conf);
		
		RouterRule rr2 = new RouterRule();
		VenusServiceConfigDO conf2 = new VenusServiceConfigDO();
		conf2.setRouterRule(rr2);

		List<VenusServiceConfigDO> serviceConfigs2 = new ArrayList<VenusServiceConfigDO>();
		serviceConfigs2.add(conf2);
		def1.setServiceConfigs(serviceConfigs);
		def2.setServiceConfigs(serviceConfigs2);
		def1.setName("orderService");
		def2.setName("userService");
		def1.setVersionRange("1.0.0");
		def2.setVersionRange("1.0.0");
		def1.setInterfaceName("com.chexiang.Orderservice");
		def2.setInterfaceName("com.chexiang.Userservice");
		def1.setDescription("中国北中华天线网");
		def2.setDescription("史可秀");

		List<VenusServiceDefinitionDO> list1=new ArrayList<VenusServiceDefinitionDO>();
		List<VenusServiceDefinitionDO> list2=new ArrayList<VenusServiceDefinitionDO>();
		list1.add(def1);
		list2.add(def2);
		List<String> jsons = new ArrayList<String>();
		jsons.add(JSON.toJSONString(list1));
		jsons.add(JSON.toJSONString(list2));
		String filePath = "D:\\Users\\longhaisheng\\venus\\old_cache.txt";
		//writeFile(filePath,  new ArrayList<String>() );
		
		Map<String, List<VenusServiceDefinitionDO>> map=new HashMap<String, List<VenusServiceDefinitionDO>>();
		map.put(RegistryUtil.getKey(list1.get(0)), list1);
		map.put(RegistryUtil.getKey(list2.get(0)), list2);
		writeFile(filePath, map);
		Map<String, List<VenusServiceDefinitionDO>> readMap = readFile(filePath);
		for (Map.Entry<String, List<VenusServiceDefinitionDO>> str : readMap.entrySet()) {
			System.out.println(str);
		}
	}*/
	
/*    public static void main(String args[]){
        HessianProxyFactory proxyFactory = new HessianProxyFactory();
        proxyFactory.setReadTimeout(5000);
        proxyFactory.setConnectTimeout(5000);
        String registerUrl="http://register.chexiang.com/registerService";
        try {
            RegisterService registerService = (RegisterService) proxyFactory.create(RegisterService.class, registerUrl);
            URL url=new URL();
            url.setInterfaceName("com.saic.usedcar.weixin.api.WeixinSendMessageService");
            url.setServiceName("weixinSendMessageService");
            List<VenusServiceDefinitionDO> findServiceDefinitions = registerService.findServiceDefinitions(url);
            System.out.println(findServiceDefinitions);
        } catch (Exception e) {
            if(exceptionLogger.isErrorEnabled()){
                exceptionLogger.error("newHessianRegisterService error.",e);
            }
        }
    }*/

	

}
