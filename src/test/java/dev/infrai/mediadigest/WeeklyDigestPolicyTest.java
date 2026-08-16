package dev.infrai.mediadigest;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyDigestPolicyTest {
    @Test
    void deliversOnlyReadyAssetsGroupedByCreator() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T09:00:00Z"), ZoneOffset.UTC);
        WeeklyDigestPolicy policy = new WeeklyDigestPolicy(clock);

        AudienceDigest digest = policy.prepare("streaming-weekly", List.of(
                new WeeklyDigestPolicy.MediaAsset("asset-02", "creator@example.com",
                        WeeklyDigestPolicy.ProcessingState.READY),
                new WeeklyDigestPolicy.MediaAsset("asset-01", "creator@example.com",
                        WeeklyDigestPolicy.ProcessingState.READY),
                new WeeklyDigestPolicy.MediaAsset("asset-03", "review@example.com",
                        WeeklyDigestPolicy.ProcessingState.PROCESSING)));

        assertThat(digest.generatedAt()).isEqualTo(Instant.parse("2026-08-10T09:00:00Z"));
        assertThat(digest.deliveries()).containsExactly(
                new AudienceDigest.CreatorDelivery("creator@example.com", List.of("asset-01", "asset-02")));
    }
}
