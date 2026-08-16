package dev.infrai.mediadigest;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class WeeklyDigestPolicy {
    private final Clock clock;

    public WeeklyDigestPolicy(Clock clock) {
        this.clock = clock;
    }

    public AudienceDigest prepare(String audienceId, List<MediaAsset> assets) {
        Map<String, List<String>> readyAssets = assets.stream()
                .filter(asset -> asset.processingState() == ProcessingState.READY)
                .collect(Collectors.groupingBy(
                        MediaAsset::creatorEmail,
                        Collectors.mapping(MediaAsset::assetId, Collectors.toList())));

        List<AudienceDigest.CreatorDelivery> deliveries = readyAssets.entrySet().stream()
                .map(entry -> new AudienceDigest.CreatorDelivery(
                        entry.getKey(), entry.getValue().stream().sorted().toList()))
                .sorted(Comparator.comparing(AudienceDigest.CreatorDelivery::creatorEmail))
                .toList();

        return new AudienceDigest(audienceId, clock.instant(), deliveries);
    }

    public record MediaAsset(String assetId, String creatorEmail, ProcessingState processingState) {
    }

    public enum ProcessingState {
        INGESTED, PROCESSING, READY
    }
}
