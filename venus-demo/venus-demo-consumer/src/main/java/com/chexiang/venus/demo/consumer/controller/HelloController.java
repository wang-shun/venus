package com.chexiang.venus.demo.consumer.controller;

import com.chexiang.venus.demo.provider.HelloService;
import com.chexiang.venus.demo.provider.model.Hello;
import com.meidusa.venus.Result;
import com.meidusa.venus.notify.InvocationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HelloController
 * Created by Zhangzhihua on 2017/9/25.
 */
@RestController
@RequestMapping("/hello")
public class HelloController {

    private static Logger logger = LoggerFactory.getLogger(HelloController.class);

    @Autowired
    HelloService helloService;

    @RequestMapping("/sayHello")
    //@ResponseBody
    public Result sayHello(){
        logger.info("testSayHello begin...");
        helloService.sayHello("jack");
        logger.info("testSayHello end...");
        return new Result("ok");
    }

    @RequestMapping("/sayHelloWithCallback")
    //@ResponseBody
    public Result sayHelloWithCallback(){
        helloService.sayHello("jack", new InvocationListener<Hello>() {

            @Override
            public void callback(Hello object) {
                logger.info("Hello:" + object);
            }

            @Override
            public void onException(Exception e) {
                logger.error("e:" + e);
            }
        });
        return new Result("callback.");
    }

    @RequestMapping("/getHello/{name}")
    //@ResponseBody
    public Result getHello(String name){
        Hello hello = null;
        try {
            logger.info("testGetHello begin...");
            hello = helloService.getHello("jack");
            logger.info("testGetHello end,result:" + hello);
        } catch (Exception e) {
            logger.error("e:{}.",e);
            return new Result(e);
        }
        return new Result(hello);
    }

}
