package com.syz.springcloud.hystrix;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.syz.springcloud.hystrix.hystrixcustomthreadpoolway.ThreadLocalHystrixConcurrencyStrategy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SyzSpcHystrixApplication {

	public static void main(String[] args) {
		//注册自定义线程池
		HystrixPlugins.getInstance().registerConcurrencyStrategy(new ThreadLocalHystrixConcurrencyStrategy());
		SpringApplication.run(SyzSpcHystrixApplication.class, args);
	}
}
