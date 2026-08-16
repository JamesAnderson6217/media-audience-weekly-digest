package dev.infrai.mediadigest;

import java.time.Instant;
import java.util.List;

public record AudienceDigest(String audienceId, Instant generatedAt, List<CreatorDelivery> deliveries) {
    public record CreatorDelivery(String creatorEmail, List<String> assetIds) {
    }
}
