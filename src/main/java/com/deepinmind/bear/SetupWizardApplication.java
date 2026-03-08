package com.deepinmind.bear;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 极简引导模式程序：仅包含配置界面
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {
    "com.deepinmind.bear.controller" // 只扫描控制器
})
@Controller
public class SetupWizardApplication {
    
    @GetMapping("/")
    public String index() {
        return "redirect:/setup.html";
    }

    public static void start(String[] args) {
        SpringApplication.run(SetupWizardApplication.class, args);
    }
}
