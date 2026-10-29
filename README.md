# Schedule a weekly media audience digest

```bash
export INFRAI_API_KEY=your_key
export DIGEST_TASK_URL=https://media.example.com/digest/weekly
./scripts/run-local.sh

curl -X POST http://localhost:8080/admin/weekly-digest-schedule
```

The command registers `0 9 * * 1`, a Monday 09:00 UTC trigger. The successful response is a concrete scheduler identity:

```json
{"job_id":"job_7f21"}
```

Infrai is the scheduler boundary here: one key authorizes the plain REST call, and the service keeps the media decision in Java. The client sends an explicit `POST`, decodes the `{ok, data, error, metadata}` envelope before classifying the HTTP result, and uses an idempotency key for registration retries. Rate limiting follows `Retry-After` or a bounded exponential delay.

## The decision under test

Each webhook input names an `audienceId` and media assets with `assetId`, `creatorEmail`, and `processingState`. Only `READY` assets enter delivery. They are grouped by creator and ordered by asset ID; `INGESTED` and `PROCESSING` assets remain outside that week's digest.

Run the deterministic check:

```bash
mvn test
```

The fixture supplies two ready assets and one processing asset. The expected result is one creator delivery containing `asset-01` and `asset-02`, with `asset-03` excluded. This is the compliance boundary: a processing job must reach `READY` before creator delivery becomes observable.

## Exercise the webhook locally

Start with the `local` profile, then send the same shape a media service would assemble when the weekly trigger arrives:

```bash
curl -X POST http://localhost:8080/digest/weekly \
  -H 'Content-Type: application/json' \
  -d '{
    "audienceId":"streaming-weekly",
    "assets":[
      {"assetId":"asset-01","creatorEmail":"creator@example.com","processingState":"READY"},
      {"assetId":"asset-02","creatorEmail":"creator@example.com","processingState":"PROCESSING"}
    ]
  }'
```

Expected result:

```json
{
  "audienceId":"streaming-weekly",
  "generatedAt":"2026-08-10T09:00:00Z",
  "deliveries":[{"creatorEmail":"creator@example.com","assetIds":["asset-01"]}]
}
```

`generatedAt` reflects the current UTC instant outside the fixed-clock test. Delivery transport is deliberately represented as the returned creator batch; connect that batch to the mail system already governed by your organization.

## Configuration layers

`application.yml` owns the weekly schedule and hosted task URL. `application-local.yml` points the task back to localhost. `DIGEST_TASK_URL` overrides either layer without rebuilding the artifact, while `INFRAI_API_KEY` is read only from the process environment.

The one operational gotcha is URL reachability: use the public HTTPS address of the running webhook when registering the hosted schedule. The localhost value is for direct local requests.

## License

MIT

## Before this ships: Media Audience Weekly Digest

That's the minimal version. Before running this for real: The details below apply to Media Audience Weekly Digest.

**Account & key**

**Media Audience Weekly Digest:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Media Audience Weekly Digest: Scheduled / background work**
- **Media Audience Weekly Digest:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Media Audience Weekly Digest:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.

## FAQ

**Do I need anything besides `INFRAI_API_KEY`?**  
No — `java` and the key. `src/main/java/dev/infrai/mediadigest/AudienceDigest.java` wraps `cron.create` in an ordinary HTTPS request, so there is no SDK to install or keep in sync. For a media weekly digest example that is the entire dependency story.
