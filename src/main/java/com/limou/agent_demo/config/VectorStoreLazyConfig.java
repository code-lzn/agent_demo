package com.limou.agent_demo.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreLazyConfig {

    @Bean
    public static BeanFactoryPostProcessor vectorStoreLazyBeanFactoryPostProcessor() {
        return beanFactory -> markLazy(beanFactory, "vectorStore");
    }

    private static void markLazy(ConfigurableListableBeanFactory beanFactory, String beanName) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return;
        }

        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
        beanDefinition.setLazyInit(true);
    }
}
