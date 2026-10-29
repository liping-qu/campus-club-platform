package com.campus.club;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校园社团管理平台后端启动类。
 */
@SpringBootApplication
@MapperScan("com.campus.club.**.mapper")
public class CampusClubApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusClubApplication.class, args);
    }
}
