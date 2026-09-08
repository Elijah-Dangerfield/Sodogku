# Grafana dashboards

The six dashboards SPEC §14 asks for, committed as JSON. One file per dashboard.

| File | uid | Answers |
| --- | --- | --- |
| `level-drop-off.json` | `sodogku-dropoff` | How far do players get, and which level stops them? |
| `difficulty-calibration.json` | `sodogku-difficulty` | Is the deduction engine's difficulty rating true? |
| `ad-funnel.json` | `sodogku-ads` | Gate to reward by placement and platform, and what no-fill costs us |
| `paywall-conversion.json` | `sodogku-paywall` | Does the paywall sell anything, and where does it fail? |
| `daily-retention.json` | `sodogku-daily` | Is the daily habit forming, and do streaks survive? |
| `tutorial-funnel.json` | `sodogku-tutorial` | Which step of the first three levels loses people? |

`difficulty-calibration.json` is the one that pays for itself. Read its description before its
panels: the shape to look for is monotonic tiers, and an inverted step between two adjacent tiers
is what a mis-rating looks like from the outside. The engine has already been wrong once and
nothing in the app surfaced it.

## What has to exist first

**Nothing on these boards has ever rendered real data.** No Sodogku build has shipped telemetry —
the Grafana Cloud OTLP endpoint and write token are still on the list in SPEC §20. Every query is
written against `docs/practices/app-events.md` and checked against the `logEvent` calls that feed
it (see "How they are kept honest" below), but none of them has been run against a Loki that holds
a single Sodogku record.

Before importing:

1. **The OTLP credentials exist.** `GRAFANA_OTLP_ENDPOINT` / `GRAFANA_OTLP_TOKEN` reach
   `loadTelemetryMetadata` in build-logic, via GitHub secrets in CI or `grafana.*` keys in
   `local.properties` for a local build. Blank values leave the pipe dormant and every panel here
   stays empty with no error anywhere — see `docs/practices/observability.md`.
2. **A build has actually run with them.** `app.launched` is the first event through the freshly
   planted tree and doubles as the pipeline smoke test. If
   `{service_name="sodogku-client"} | event_name="app.launched"` returns nothing in Explore, stop
   here; the dashboards will not tell you why.
3. **The Loki datasource uid matches.** Every panel targets `grafanacloud-logs`, which is the uid
   Grafana Cloud provisions its Loki datasource under in every stack. If a stack differs, rewrite
   it in one pass:

   ```bash
   printf "Loki datasource uid: "; read -r UID
   [ -n "$UID" ] && sed -i '' "s/grafanacloud-logs/$UID/g" ops/grafana/*.json && unset UID
   ```

   Confirm the uid first from **Connections → Data sources → (your Loki) → the URL**, or with
   `/api/datasources` on the stack.

## Importing

Per dashboard, in the Grafana UI: **Dashboards → New → Import → Upload JSON file**. The uid is in
the file, so re-importing the same file updates the existing dashboard rather than making a second
copy. There is no folder pinned in the JSON — pick one at import time.

Or over the API, all six at once:

```bash
printf "Grafana URL (e.g. https://myorg.grafana.net): "; read -r URL
stty -echo; printf "Grafana API token (Editor or Admin): "; read -r TOKEN; stty echo; printf "\n"
for f in ops/grafana/*.json; do
  jq -n --slurpfile d "$f" '{dashboard: $d[0], overwrite: true, folderUid: ""}' |
    curl -sS -X POST "$URL/api/dashboards/db" \
      -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" --data @- |
    jq -r '"\(.status // "ok")\t\(.uid // .message)"'
done
unset TOKEN URL
```

Import is the whole deployment story. Nothing provisions these automatically, and nothing syncs
edits back — a change made in the UI is lost on the next import, so edit the JSON.

## Reading them

**Two stream labels, everything else is structured metadata.** `service_name` and
`deployment_environment` are the only labels; `event_name`, `session_id`, `install_id` and every
event attribute are filtered with pipes. Line filters (`|=`) would work and are wrong — they scan
the body instead of the metadata.

**The `$env` dropdown is `prod` / `dev`**, and it maps to `deployment.environment` on the export,
which is `dev` for a debug build and `prod` for a release one. A dashboard reading `prod` while you
test a debug build shows nothing.

**Delivery is at-least-once.** A record can rarely ship twice — the export was acknowledged and the
process died before the disk buffer's delete. Raw counts are therefore approximate. Panels that
count *installs* rather than records are immune by construction, because a duplicate lands in the
same `install_id` series and collapses; that is why the drop-off curve, the daily DAU and the
tutorial funnel are all written that way. Medians and rates are ratios of two equally affected
counts, so they barely move.

**Sampling is per session, not per event.** `telemetry.appEventsSampleRate` hashes the session id,
so a sampled-out session contributes nothing at all rather than losing the middle of a funnel. If
the rate is below 1.0, every absolute count on these boards is that fraction of reality; every
ratio is unaffected.

## How they are kept honest

`DashboardQueryContractTest` (`:libraries:telemetry:impl`, runs under `./gradlew testDebugUnitTest`)
parses every query in this directory and every `logEvent(...)` call in the source tree, and fails if
a dashboard references an event or an attribute nothing emits.

This is the check that a rename would otherwise pass. A panel filtering on `strikes_used` against
an app emitting `strikes` is not an error anywhere: Loki accepts the query, the panel renders, and
it renders **empty** — indistinguishable from "nobody has played yet".

Two consequences for anyone editing these files:

- **The LogQL reader is strict on purpose.** It understands the stream selector, label filters and
  `unwrap`, and throws on anything else. Reaching for `| json`, `| logfmt`, `| line_format` or
  `| label_format` fails the test rather than slipping through with nothing extracted. If a panel
  genuinely needs one, teach the reader first.
- **Ratios go through `__expr__` math**, two Loki targets and a math node, following the house
  style. A single expression naming two events is allowed, but then every attribute in it must
  exist on *both* — which is correct, and occasionally stricter than you want.
