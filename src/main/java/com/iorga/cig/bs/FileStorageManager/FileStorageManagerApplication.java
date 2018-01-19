package com.iorga.cig.bs.FileStorageManager;

import com.iorga.cig.bs.FileStorageManager.controllers.FileStorageController;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan
public class FileStorageManagerApplication {

	public static void main(String[] args) {
		SpringApplication sa = new SpringApplication(FileStorageManagerApplication.class);
		sa.setBannerMode(Banner.Mode.CONSOLE);
		sa.setLogStartupInfo(false);
		sa.run(args);
	}
}
