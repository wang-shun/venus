package com.meidusa.venus;

import com.meidusa.toolkit.common.bean.BeanContext;
import org.springframework.context.ApplicationContext;

/**
 * venus应用上下文信息
 * Created by Zhangzhihua on 2017/8/28.
 */
public class VenusContext {

    private static VenusContext venusContext;

    private ApplicationContext applicationContext;

    private BeanContext beanContext;

    public static VenusContext getInstance(){
        if(venusContext == null){
            venusContext = new VenusContext();
        }
        return venusContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public BeanContext getBeanContext() {
        return beanContext;
    }

    public void setBeanContext(BeanContext beanContext) {
        this.beanContext = beanContext;
    }
}