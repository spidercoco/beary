package com.deepinmind.bear;

import com.deepinmind.bear.utils.ConfigManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Properties;

@SpringBootApplication
public class DemoApplication implements CommandLineRunner {

    public static void main(String[] args) {
        if (!ConfigManager.isConfigured()) {
            // --- 阶段一：引导模式 ---
            System.out.println("====================================================");
            System.out.println(">>> 小熊AI：未检测到初始化配置。");
            System.out.println(">>> 请访问 http://localhost:8080 完成初始化设置。");
            System.out.println("====================================================");
            SetupWizardApplication.start(args);
        } else {
            // --- 阶段二：正式业务模式 ---
            System.out.println(">>> 检测到初始化配置，正在加载主业务程序...");
            
            SpringApplication app = new SpringApplication(DemoApplication.class);
            
            // 动态注入外部配置文件
            Properties props = new Properties();
            props.setProperty("spring.config.additional-location", "file:beary_info/conf/application.properties");
            app.setDefaultProperties(props);
            
            app.run(args);
        }
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> 小熊AI 主程序已启动成功！");
    }
}
