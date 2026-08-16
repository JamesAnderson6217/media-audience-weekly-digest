package dev.infrai.mediadigest;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "digest")
public record DigestConfiguration(String cronExpression, String taskUrl, String infraiBaseUrl) {
}
