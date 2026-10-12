package com.mk.daftar.feature.home

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that masking removes the underlying figures from the accessibility tree, not just from what is drawn. Every
 * semantics property of every node, in both the merged and the unmerged tree, is searched for the private values.
 */
@RunWith(AndroidJUnit4::class)
class HomeMaskingSemanticsTest {

    @get:Rule
    val rule = createAndroidComposeRule<HomeScreenshotHostActivity>()

    /** Figures that the masked screen must never expose: totals, category amounts, the subtotal and account balances. */
    private val privateValues = listOf(
        "1,076,212.74",
        "677,812.74",
        "388,500.00",
        "9,900.00",
        "1,058,772.45",
        "163,500",
        "180,000",
        "45,000",
        "4,900",
        "5,000",
    )

    @Test
    fun maskedValuesAreAbsentFromMergedAndUnmergedSemantics() {
        show(HomeUiState(load = HomeLoad.Ready(readyFigures()), valuesHidden = true))
        for (unmerged in listOf(false, true)) {
            val tree = semanticsText(rule.onRoot(useUnmergedTree = unmerged))
            for (value in privateValues) {
                assertTrue(!tree.contains(value), "masked value $value leaked (unmerged=$unmerged)")
            }
            assertTrue(tree.contains("Hidden"), "the masked placeholder is announced (unmerged=$unmerged)")
        }
    }

    @Test
    fun unmaskedValuesArePresentSoTheSearchIsMeaningful() {
        show(HomeUiState(load = HomeLoad.Ready(readyFigures()), valuesHidden = false))
        val tree = semanticsText(rule.onRoot(useUnmergedTree = true))
        assertTrue(tree.contains("1,076,212.74"), "the total is announced when visible")
    }

    private fun show(state: HomeUiState) {
        rule.runOnUiThread { rule.activity.fake = HomeScreenshotHostActivity.Frame(state, dark = false) }
        rule.waitForIdle()
    }

    /** Everything each node says: its configuration as text, flattened over the whole tree. */
    private fun semanticsText(root: SemanticsNodeInteraction): String = collect(root.fetchSemanticsNode())

    private fun collect(node: SemanticsNode): String =
        node.config.toString() + "\n" + node.children.joinToString("\n") { collect(it) }
}
