package com.miu.codemain;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = RedisEmbeddingStoreAutoConfiguration.class)
@MapperScan("com.miu.codemain.mapper")
public class AiPageGenBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiPageGenBackendApplication.class, args);
	}

}
