package com.calore.tracker

/**
 * Switches for features that are still being built (implementation rule 9).
 *
 * Debug builds show every feature, so it can be tried while it is built. Release builds show only
 * finished features. Add one property per unfinished feature and guard each of its entry points
 * (tab, button, route) with it:
 *
 * ```
 * val cycleTracking: Boolean = unfinished()
 * ```
 *
 * When the feature is finished, delete its flag and the checks.
 */
object FeatureFlags {

    // No unfinished features right now: everything up to M9 is finished.

    /**
     * M10: "Help research" shows only when the build has a research server address
     * (`calore.researchServerUrl`). Without one, nothing about research is shown or sent.
     */
    val research: Boolean = BuildConfig.RESEARCH_SERVER_URL.isNotBlank()

    /**
     * True only in debug builds. [BuildConfig.DEBUG] is a constant, so R8 removes code behind a
     * false flag from release builds completely.
     */
    @Suppress("unused") // Used by the flags above as soon as a milestone adds one.
    private fun unfinished(): Boolean = isVisible(finished = false, debugBuild = BuildConfig.DEBUG)

    /** The rule behind every flag: finished features always show; unfinished ones only in debug builds. */
    internal fun isVisible(finished: Boolean, debugBuild: Boolean): Boolean = finished || debugBuild
}
