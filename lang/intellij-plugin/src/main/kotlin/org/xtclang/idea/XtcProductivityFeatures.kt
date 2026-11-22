package org.xtclang.idea

import com.intellij.featureStatistics.ProductivityFeaturesProvider

/**
 * Ecstasy's features for Tip of the Day and Help | My Productivity, described in [FEATURES], which IntelliJ reads
 * through the plugin's class loader. Each feature names its tip and the actions that count as its uses, so the tips
 * of features the user already uses come after the others.
 */
class XtcProductivityFeatures : ProductivityFeaturesProvider() {
    override fun getXmlFilesUrls(): Collection<String> = listOf(FEATURES)

    companion object {
        const val FEATURES = "XtcProductivityFeatures.xml"
    }
}
