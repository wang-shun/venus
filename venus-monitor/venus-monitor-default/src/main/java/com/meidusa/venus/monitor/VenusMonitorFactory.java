package com.meidusa.venus.monitor;

import com.athena.service.api.AthenaDataService;
import com.meidusa.venus.RpcException;
import com.meidusa.venus.ServiceFactoryBean;
import com.meidusa.venus.ServiceFactoryExtra;
import com.meidusa.venus.support.VenusContext;
import com.meidusa.venus.exception.VenusConfigException;
import com.meidusa.venus.monitor.config.ClientConfigManagerDelegate;
import com.meidusa.venus.monitor.support.CustomScanAndRegisteUtil;
import com.meidusa.venus.monitor.support.ApplicationContextHolder;
import com.meidusa.venus.util.ReftorUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * venus监控工厂类
 * Created by Zhangzhihua on 2017/9/11.
 */
public class VenusMonitorFactory implements InitializingBean, ApplicationContextAware,BeanFactoryPostProcessor {

    private static Logger logger = LoggerFactory.getLogger(VenusMonitorFactory.class);

    /**
     * 注册中心url地址，injvm或者远程注册中心服务地址
     */
    private String url;

    private boolean enable;

    /**
     * athena配置信息管理委托
     */
    private ClientConfigManagerDelegate clientConfigManagerDelegate;

    /**
     * athena配置信息管理
     */
    private Object clientConfigManager;

    /**
     * athena上报服务接口
     */
    private AthenaDataService athenaDataService;

    private ServiceFactoryExtra serviceFactoryExtra;

    private ApplicationContext applicationContext;

    //private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    @Override
    public void afterPropertiesSet() throws Exception {
        valid();

        init();
    }

    /**
     * 校验
     */
    void valid(){
        if(StringUtils.isEmpty(url)){
            throw new VenusConfigException("url not allow empty.");
        }
    }

    void init(){
        //手动扫描athena以注解定义的包
        scanAndRegisteAthenaPackage();

        //初始化simpleServiceFactory
        initSimpleServiceFactory();

        //初始化athenaConfigManager
        initAthenaConfigManager();

        //初始化athenaDataService
        initAthenaDataService(url);
    }


    void scanAthenaPackageByResource(){
        //String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "/com/saic/framework/**/*.class";
        /*
        try {
            Resource[] resources = resourcePatternResolver.getResources(packageSearchPath);
            for (Resource resource : resources) {
                logger.info("resource:{}.",resource);
            }
        } catch (IOException e) {
            logger.error("scan athena client package error.",e);
        }
        */
    }

    /**
     * 手动扫描athena client包并注册到spring上下文
     */
    void scanAndRegisteAthenaPackage(){
        ApplicationContextHolder contextHolder = new ApplicationContextHolder();
        contextHolder.setApplicationContext(applicationContext);

        CustomScanAndRegisteUtil scanner = new CustomScanAndRegisteUtil();
        String[] confPkgs = {"/com/saic/framework"};
        Class[] annotationTags = {Component.class,Service.class};
        Set<Class<?>> classSet = scanner.scan(confPkgs,annotationTags);
        for(Class cls:classSet){
            if(logger.isDebugEnabled()){
                logger.debug("cls:{}.",cls);
            }
        }
        scanner.regist(classSet);
    }

    /**
     * 初始化simpleServiceFactory
     */
    void initSimpleServiceFactory(){
        String className = "com.meidusa.venus.client.factory.simple.SimpleServiceFactory";
        Object obj = ReftorUtil.newInstance(className);
        if(obj == null){
            throw new VenusConfigException("init simpleServiceFactory failed.");
        }
        this.serviceFactoryExtra = (ServiceFactoryExtra)obj;
    }

    /**
     * 初始化athena配置信息
     */
    void initAthenaConfigManager(){
        //创建配置信息代理实例
        String className = "com.meidusa.venus.monitor.athena.config.impl.DefaultClientConfigManagerDelegate";
        ClientConfigManagerDelegate clientConfigManagerDelegate = ReftorUtil.newInstance(className);
        if(clientConfigManagerDelegate == null){
            throw new VenusConfigException("instance clientConfigManager failed.");
        }
        this.clientConfigManagerDelegate = clientConfigManagerDelegate;

        //初始化配置信息实例
        String appName = VenusContext.getInstance().getApplication();
        if(StringUtils.isEmpty(appName)){
            throw new VenusConfigException("application not inited.");
        }
        Object clientConfigManager = clientConfigManagerDelegate.initConfigManager(appName,true);
        this.clientConfigManager = clientConfigManager;
    }

    /**
     * 初始化athenaDataService
     * @param url
     */
    void initAthenaDataService(String url){
        /*
        String[] arr = url.split(":");
        SimpleServiceFactory factory = new SimpleServiceFactory(arr[0],Integer.parseInt(arr[1]));
        factory.setSoTimeout(16 * 1000);//可选,默认 15秒
        factory.setCoTimeout(5 * 1000);//可选,默认5秒
        AthenaDataService athenaDataService = factory.getService(AthenaDataService.class);
        */
        String[] addressArr = {url};
        //List<String> addressList = Arrays.asList(address);
        serviceFactoryExtra.setAddressList(addressArr);
        AthenaDataService athenaDataService = serviceFactoryExtra.getService(AthenaDataService.class);

        if(athenaDataService == null){
            throw new RpcException("init athenaDataService failed.");
        }
        this.athenaDataService = athenaDataService;
        AthenaContext.getInstance().setAthenaDataService(athenaDataService);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        //注册configManager
        registeClientConfigManager(beanFactory);

        //注册athenaDataService到上下文
        registeAthenaDataService(beanFactory);
    }

    /**
     * 注册configManager
     * @param beanFactory
     */
    void registeClientConfigManager(ConfigurableListableBeanFactory beanFactory){
        clientConfigManagerDelegate.registeConfigManager(beanFactory,this.clientConfigManager);
        /*
        String beanName = clientConfigManager.getClass().getSimpleName();
        BeanDefinitionRegistry reg = (BeanDefinitionRegistry) beanFactory;
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(ServiceFactoryBean.class);
        beanDefinition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, beanName));
        beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);
        ConstructorArgumentValues args = new ConstructorArgumentValues();
        args.addIndexedArgumentValue(0, this.clientConfigManager);
        args.addIndexedArgumentValue(1, ClientConfigManager.class);
        beanDefinition.setConstructorArgumentValues(args);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);
        reg.registerBeanDefinition(beanName, beanDefinition);
        */
    }

    /**
     * 注册athenaDataService
     * @param beanFactory
     */
    void registeAthenaDataService(ConfigurableListableBeanFactory beanFactory){
        String beanName = athenaDataService.getClass().getSimpleName();
        BeanDefinitionRegistry reg = (BeanDefinitionRegistry) beanFactory;
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(ServiceFactoryBean.class);
        beanDefinition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, beanName));
        beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);
        ConstructorArgumentValues args = new ConstructorArgumentValues();
        args.addIndexedArgumentValue(0, this.athenaDataService);
        args.addIndexedArgumentValue(1, AthenaDataService.class);
        beanDefinition.setConstructorArgumentValues(args);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);
        reg.registerBeanDefinition(beanName, beanDefinition);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public AthenaDataService getAthenaDataService() {
        return athenaDataService;
    }

    public void setAthenaDataService(AthenaDataService athenaDataService) {
        this.athenaDataService = athenaDataService;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
