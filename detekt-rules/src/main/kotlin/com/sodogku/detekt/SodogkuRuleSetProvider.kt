package com.sodogku.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Registers the project's custom rule set. Discovered by detekt via the
 * `META-INF/services/dev.detekt.api.RuleSetProvider` entry. The rule set id
 * (`sodogku`) namespaces the rules in `detekt.yml`.
 */
class SodogkuRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("sodogku")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ::VerifyStrings,
            ::AnimatedStateReadInComposition,
            ::NoRawDesignValues,
        ),
    )
}
