package dev.infrai.mediadigest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;

@RestController
public final class DigestDeliveryController {
    private final InfraiCronClient cronClient;
    private final WeeklyDigestPolicy digestPolicy = new WeeklyDigestPolicy(Clock.systemUTC());

    public DigestDeliveryController(InfraiCronClient cronClient) {
        this.cronClient = cronClient;
    }

    @PostMapping("/admin/weekly-digest-schedule")
    public Map<String, String> registerSchedule() throws Exception {
        String jobId = cronClient.createWeeklyDigest("media-weekly-digest-v1");
        return Map.of("job_id", jobId);
    }

    @PostMapping("/digest/weekly")
    public AudienceDigest deliver(@RequestBody DigestRequest request) {
        List<WeeklyDigestPolicy.MediaAsset> assets = request.assets().stream()
                .map(asset -> new WeeklyDigestPolicy.MediaAsset(asset.assetId(), asset.creatorEmail(),
                        WeeklyDigestPolicy.ProcessingState.valueOf(asset.processingState())))
                .toList();
        return digestPolicy.prepare(request.audienceId(), assets);
    }

    @ExceptionHandler(InfraiCronClient.InfraiException.class)
    public ResponseEntity<Map<String, Object>> rejected(InfraiCronClient.InfraiException error) {
        int status = error.statusCode() >= 400 && error.statusCode() < 500 ? error.statusCode() : 502;
        return ResponseEntity.status(status).body(Map.of("code", error.code(), "message", error.getMessage()));
    }

    public record DigestRequest(String audienceId, List<AssetInput> assets) {
    }

    public record AssetInput(String assetId, String creatorEmail, String processingState) {
    }
}
