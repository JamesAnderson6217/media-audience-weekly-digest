package dev.infrai.mediadigest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DigestConfiguration.class)
public class MediaDigestApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediaDigestApplication.class, args);
    }
}
