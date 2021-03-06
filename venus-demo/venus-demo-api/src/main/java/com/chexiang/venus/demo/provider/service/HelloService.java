package com.chexiang.venus.demo.provider.service;

import com.chexiang.venus.demo.provider.exception.HelloValidException;
import com.chexiang.venus.demo.provider.exception.InvalidParamException;
import com.chexiang.venus.demo.provider.model.Hello;
import com.chexiang.venus.demo.provider.model.HelloEx;
import com.meidusa.venus.annotations.Endpoint;
import com.meidusa.venus.annotations.Param;
import com.meidusa.venus.annotations.Service;
import com.meidusa.venus.notify.InvocationListener;

import java.util.List;

/**
 * Created by Zhangzhihua on 2017/8/15.
 */
@Service(name = "helloService",version = 6,description = "venus hello示例服务")
public interface HelloService {

    /**
     * sayHello
     * @param name
     */
    @Endpoint(name = "sayHello")
    void sayHello(@Param(name="name") String name);

    /**
     * sayHello
     * @param name
     */
    @Endpoint(name = "sayHelloWithCallback")
    void sayHello(@Param(name="name") String name,@Param(name="callback")InvocationListener<Hello> invocationListener);

    @Endpoint(name = "sayHelloForException")
    int sayHelloForException(@Param(name = "param")int param) throws HelloValidException,InvalidParamException;

    /**
     * getHello
     * @param name
     * @return
     */
    @Endpoint(name = "getHello")
    Hello getHello(@Param(name = "name") String name);

    @Endpoint(name = "getHelloForBench")
    HelloEx getHelloForBench(@Param(name = "name") String name, @Param(name = "params") byte[] params);

    @Endpoint(name = "queryHello")
    List<Hello> queryHello(@Param(name = "name") String name);
    

}
