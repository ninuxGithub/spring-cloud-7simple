/*
 * Copyright 2012-2020 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * @author lzhoumail@126.com/zhouli
 * Git http://git.oschina.net/zhou666/spring-cloud-7simple
 */
package cloud.simple.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import cloud.simple.model.User;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

@Service
public class UserService {
	private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    RestTemplate restTemplate;

    //	 @Autowired
    //	 FeignUserService feignUserService;

    final String SERVICE_NAME = "cloud-simple-service";

    @SuppressWarnings("unchecked")
	@HystrixCommand(fallbackMethod = "fallbackSearchAll")
    public List<User> readUserInfo() {
    	String url = "http://" + SERVICE_NAME + "/user";
    	logger.info("readUserInfo===>"+url);
        return restTemplate.getForObject(url, List.class);
        //return feignUserService.readUserInfo();
    }

    @SuppressWarnings("unused")
	private List<User> fallbackSearchAll() {
        System.out.println("HystrixCommand fallbackMethod handle!");
        List<User> ls = new ArrayList<User>();
        User user = new User();
        user.setId(1);
        user.setUsername("TestHystrixCommand");
        ls.add(user);
        return ls;
    }
}
