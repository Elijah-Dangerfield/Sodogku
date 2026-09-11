package com.sodogku.integration.config

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.impl.ConfigRefreshThrottleMs
import com.sodogku.libraries.config.values.SodogkuConfigValues
import com.sodogku.libraries.telemetry.impl.AppEventsEnabled
import com.sodogku.libraries.telemetry.impl.AppEventsSampleRate
import com.sodogku.libraries.telemetry.impl.KlogForwardingEnabled

/**
 * Every `ConfiguredValue` the app declares, from the one module that can see all
 * of them.
 *
 * [SodogkuConfigValues] covers `:libraries:config` and is the bulk of it. The
 * rest are declared in impl modules that `:libraries:config` cannot depend on,
 * so they are named here: the `telemetry.*` three in `:libraries:telemetry:impl`,
 * and [ConfigRefreshThrottleMs] in `:libraries:config:impl`, which sits next to
 * the repository whose refresh it throttles.
 *
 * Adding a value class anywhere and forgetting this list is the mistake that hid
 * `config.refreshThrottleMs` from every completeness check the repo has.
 * `ConfigDeclarationsAreEnumeratedTest` is the guard: it reads the declarations
 * out of the source tree and fails on one this function does not return.
 */
internal fun declaredConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> =
    SodogkuConfigValues.all(appConfigMap) +
        AppEventsEnabled(appConfigMap) +
        AppEventsSampleRate(appConfigMap) +
        KlogForwardingEnabled(appConfigMap) +
        ConfigRefreshThrottleMs(appConfigMap)
