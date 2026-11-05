package com.devflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DevFlow 后端服务启动类
 * 企业级 AI 协作研发平台
 */
@SpringBootApplication
@MapperScan("com.devflow.mapper")
@EnableAsync
@EnableScheduling
public class DevFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevFlowApplication.class, args);
    }
}
