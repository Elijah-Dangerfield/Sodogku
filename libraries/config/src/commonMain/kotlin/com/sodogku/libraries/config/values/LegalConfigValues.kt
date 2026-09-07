package com.sodogku.libraries.config.values

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.ConfiguredValue
import com.sodogku.libraries.config.IntConfigValue
import com.sodogku.libraries.config.QaConfigValue
import com.sodogku.libraries.config.StringConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Current terms version. Compared against the version recorded in `AppData` at
 * launch: behind it shows a dismissible banner, behind [LegalForceReacceptBelow]
 * shows a blocking sheet. Living in config is the point — publishing new terms
 * becomes a config change rather than a store release.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalTermsVersion(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Terms version"
    override val path = "legal.termsVersion"
    override val default = 1
}

/** Where the terms are published. GitHub Pages serves them from `pages/terms.html`. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalTermsUrl(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Terms URL"
    override val path = "legal.termsUrl"
    override val default = "https://elijah-dangerfield.github.io/Sodogku/terms.html"
}

/** Current privacy policy version. Tracked separately from terms; the two change independently. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalPrivacyVersion(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Privacy version"
    override val path = "legal.privacyVersion"
    override val default = 1
}

/**
 * Where the privacy policy is published. Both stores require this URL in the
 * listing as well, so the two must not drift.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalPrivacyUrl(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Privacy URL"
    override val path = "legal.privacyUrl"
    override val default = "https://elijah-dangerfield.github.io/Sodogku/privacy.html"
}

/**
 * An accepted version below this forces a blocking re-acceptance sheet instead of
 * a dismissible banner.
 *
 * Zero means nothing is forced, which is the safe fallback: a blocking legal
 * sheet raised by a stale or half-written config is a server problem locking a
 * player out of a game they already had. Raise it deliberately, in the same
 * config change that raises [LegalTermsVersion], when the terms change materially
 * enough to need consent again.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalForceReacceptBelow(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Force re-accept below version"
    override val path = "legal.forceReacceptBelow"
    override val default = 0
}

/** Every `legal.*` value. Registered in [SodogkuConfigValues]. */
fun legalConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> = listOf(
    LegalTermsVersion(appConfigMap),
    LegalTermsUrl(appConfigMap),
    LegalPrivacyVersion(appConfigMap),
    LegalPrivacyUrl(appConfigMap),
    LegalForceReacceptBelow(appConfigMap),
)
