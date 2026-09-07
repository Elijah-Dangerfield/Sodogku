package com.sodogku.libraries.core

import com.sodogku.buildinfo.SodogkuBuildConfig

/**
 * Build-time-injected telemetry credentials, mirroring [SupabaseInfo]'s
 * pattern: CI reads repo secrets, local builds read `local.properties`
 * (`sentry.dsn`, `grafana.*` keys — see `loadTelemetryMetadata` in
 * build-logic). Blank values mean the corresponding pipe stays dormant —
 * telemetry no-ops rather than failing, so a fresh clone builds and runs
 * with zero setup.
 */
object TelemetryInfo {
    /** Single Sentry DSN for all platforms/build types. The `environment`
     *  tag (releaseChannel-platform-buildType) separates them within one
     *  project. Blank → crash reporting disabled. */
    val sentryDsn: String
        get() = SodogkuBuildConfig.SENTRY_DSN

    val grafanaOtlpBaseUrl: String
        get() = SodogkuBuildConfig.GRAFANA_OTLP_BASE_URL

    val grafanaOtlpInstanceId: String
        get() = SodogkuBuildConfig.GRAFANA_OTLP_INSTANCE_ID

    val grafanaLogsWriteToken: String
        get() = SodogkuBuildConfig.GRAFANA_LOGS_WRITE_TOKEN
}
