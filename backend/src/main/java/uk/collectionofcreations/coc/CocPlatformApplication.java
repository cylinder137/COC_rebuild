package uk.collectionofcreations.coc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 造物集（coc-platform）后端启动类。
 * 试运行版 MVP：端口 8090。
 */
@SpringBootApplication
@MapperScan("uk.collectionofcreations.coc.mapper")
public class CocPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CocPlatformApplication.class, args);
    }
}
