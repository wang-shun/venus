package com.meidusa.venus.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

/**
 * Created by Zhangzhihua on 2017/9/21.
 */
/*
@SpringBootApplication(exclude= {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class
        })
@ImportResource("classpath:conf/application-venus-server.xml")
*/
public class RegisterEmbeddedTomcatApplication {

    public static final Logger logger = LoggerFactory.getLogger(RegisterEmbeddedTomcatApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(RegisterEmbeddedTomcatApplication.class, args);
    }

}
