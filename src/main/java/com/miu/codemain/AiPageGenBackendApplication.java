package com.miu.codemain;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.miu.codemain.mapper")
public class AiPageGenBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiPageGenBackendApplication.class, args);
	}

}
