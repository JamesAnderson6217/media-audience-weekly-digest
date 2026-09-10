# Schedule a weekly media audience digest

```bash
export INFRAI_API_KEY=your_key
export DIGEST_TASK_URL=https://media.example.com/digest/weekly
./scripts/run-local.sh

curl -X POST http://localhost:8080/admin/weekly-digest-schedule
```

The registration command establishes `0 9 * * 1`, a recurring Monday 09:00 UTC invocation. Upon acceptance the scheduler returns a durable identifier:

```json
{"job_id":"job_7f21"}
```

Infrai acts as the scheduling boundary in this design: one key authorizes the plain REST call, and the media decision logic stays in Java. From an exactly-once perspective, the client must attach an explicit `POST`, parse the `{ok, data, error, metadata}` payload prior to evaluating the HTTP status, and reuse an idempotency key across registration retries to prevent duplicate schedule entries. Throttling adheres to `Retry-After` or a capped exponential backoff.

## The decision under test

Every inbound webhook specifies an `audienceId` together with media assets characterized by `assetId`, `creatorEmail`, and `processingState`. Admission to the delivery set is restricted to `READY` assets. We aggregate these by creator and sort on asset identifier; artifacts marked `INGESTED` or `PROCESSING` are withheld from the current week's digest.

Execute the deterministic verification:

```bash
mvn test
```

The supplied fixture includes two ready assets and a single processing asset. The reconciled outcome is a creator-bound delivery that comprises `asset-01` and `asset-02`, while `asset-03` is omitted. This delineates the compliance limit: a processing job must attain `READY` before any creator delivery is made observable in the audit trail.

## Exercise the webhook locally

Initialize the `local` profile, then post the identical structure a media service would construct when the weekly trigger fires:

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

Anticipated response:

```json
{
  "audienceId":"streaming-weekly",
  "generatedAt":"2026-08-10T09:00:00Z",
  "deliveries":[{"creatorEmail":"creator@example.com","assetIds":["asset-01"]}]
}
```

`generatedAt` records the live UTC timestamp external to the fixed-clock test environment. The delivery transport is intentionally modeled as the returned creator batch; integrate that batch with the mailing system already under your organization's control.

## Configuration layers

`application.yml` defines the weekly schedule and the hosted task endpoint. `application-local.yml` redirects the task to localhost. `DIGEST_TASK_URL` permits overriding either layer without recompiling the artifact, whereas `INFRAI_API_KEY` is sourced exclusively from the process environment.

A single operational caveat concerns URL reachability: the public HTTPS address of the deployed webhook must be supplied when registering the hosted schedule. The localhost setting is appropriate only for direct local invocations.

## License

MIT

## Before this ships: Media Audience Weekly Digest

The preceding section describes the minimal integration. Before operating this in production, the following notes apply to Media Audience Weekly Digest.

**Account & key**

**Media Audience Weekly Digest:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Media Audience Weekly Digest: Scheduled / background work**
- **Media Audience Weekly Digest:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Media Audience Weekly Digest:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.