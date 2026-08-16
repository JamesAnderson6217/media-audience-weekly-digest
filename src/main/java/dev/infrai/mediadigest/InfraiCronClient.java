package dev.infrai.mediadigest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
public final class InfraiCronClient {
    private static final int MAX_ATTEMPTS = 4;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DigestConfiguration configuration;
    private final String apiKey;

    public InfraiCronClient(ObjectMapper objectMapper, DigestConfiguration configuration) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper,
                configuration, requiredEnvironment("INFRAI_API_KEY"));
    }

    InfraiCronClient(HttpClient httpClient, ObjectMapper objectMapper,
                     DigestConfiguration configuration, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.configuration = configuration;
        this.apiKey = apiKey;
    }

    // cron.create: POST /v1/cron/create
    public String createWeeklyDigest(String idempotencyKey) throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "cron_expr", configuration.cronExpression(),
                "task", configuration.taskUrl()));

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(configuration.infraiBaseUrl() + "/v1/cron/create"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = objectMapper.readTree(response.body());

            if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!envelope.path("ok").asBoolean(false)) {
                JsonNode error = envelope.path("error");
                throw new InfraiException(error.path("code").asText("INFRAI_REJECTED"),
                        error.toString(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport response " + response.statusCode());
            }
            return envelope.path("data").path("job_id").asText();
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        if (!value.isBlank()) {
            try {
                return Math.max(0, Long.parseLong(value) * 1_000L);
            } catch (NumberFormatException ignored) {
                try {
                    return Math.max(0, Duration.between(ZonedDateTime.now(),
                            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis());
                } catch (DateTimeParseException ignoredDate) {
                    // Fall through to bounded exponential delay.
                }
            }
        }
        return 250L * (1L << attempt);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int statusCode;

        InfraiException(String code, String detail, int statusCode) {
            super(detail);
            this.code = code;
            this.statusCode = statusCode;
        }

        public String code() {
            return code;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
