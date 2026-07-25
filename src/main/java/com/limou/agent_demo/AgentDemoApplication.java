package com.limou.agent_demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentDemoApplication {

    public static void main(String[] args) {
        // 关闭 headless 模式，允许使用 java.awt.Desktop 打开文件/目录
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(AgentDemoApplication.class, args);
    }

}
