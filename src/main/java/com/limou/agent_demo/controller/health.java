package com.limou.agent_demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class health {
    //生成健康检查接口
    @GetMapping("/health")
    public String test(){
        return "ok";
    }
}
