package com.iorga.cig.bs.FileStorageManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@SpringBootApplication
@ComponentScan(lazyInit = true)
public class FileStorageManagerApplication {

    public static void main(String[] args) {
        SpringApplication sa = new SpringApplication(FileStorageManagerApplication.class);
        sa.setBannerMode(Banner.Mode.CONSOLE);
        sa.setLogStartupInfo(false);
        sa.run(args);
    }

    @EnableWebSecurity
    @Configuration
    public class CustomWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

        @Value("${security.access.ipmask.el}")
        private String accessIpMask;

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            // Disable CSRF
            http.csrf().disable();

            http.authorizeRequests()
//                .antMatchers("/api/**").access(accessIpMask)
                .anyRequest().permitAll();
        }
    }
}
