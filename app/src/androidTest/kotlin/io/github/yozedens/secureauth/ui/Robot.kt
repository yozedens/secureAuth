package io.github.yozedens.secureauth.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yozedens.secureauth.R

/** Keystore, PBKDF2 and DataStore work runs off the main thread, so waits are generous. */
const val TIMEOUT_MILLIS = 20_000L

private val context: Context get() = ApplicationProvider.getApplicationContext()

fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

fun ComposeTestRule.waitFor(matcher: SemanticsMatcher) =
    await("node ${matcher.description}") { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

/** Waits for [condition]; on timeout, names what was awaited and dumps every window's tree. */
fun ComposeTestRule.await(what: String, condition: () -> Boolean) {
    try {
        waitUntil(TIMEOUT_MILLIS, condition)
    } catch (timeout: ComposeTimeoutException) {
        val screen = try {
            onAllNodes(isRoot()).printToString()
        } catch (ignored: Throwable) {
            "<semantics tree unavailable>"
        }
        throw AssertionError("Timed out waiting for $what. Screen:\n$screen", timeout)
    }
}

fun ComposeTestRule.waitForText(text: String) = waitFor(hasText(text))

fun ComposeTestRule.waitUntilGone(text: String) =
    await("\"$text\" to disappear") { onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }

fun ComposeTestRule.click(text: String) {
    waitForText(text)
    onNode(hasText(text)).scrollIfPossible().performClick()
}

fun ComposeTestRule.type(label: String, text: String) {
    val field = hasSetTextAction() and hasText(label)
    waitFor(field)
    onNode(field).scrollIfPossible().performTextInput(text)
}

fun ComposeTestRule.replace(label: String, text: String) {
    val field = hasSetTextAction() and hasText(label)
    waitFor(field)
    onNode(field).scrollIfPossible().performTextReplacement(text)
}

fun ComposeTestRule.toggle() {
    waitFor(isToggleable())
    onNode(isToggleable()).scrollIfPossible().performClick()
}

/** First launch: acknowledge the risk, set a PIN, land on the empty account list. */
fun ComposeTestRule.onboard(pin: String) {
    waitForText(str(R.string.onboarding_acknowledge))
    toggle()
    click(str(R.string.action_continue))
    type(str(R.string.pin_setup_hint), pin)
    type(str(R.string.pin_confirm_hint), pin)
    click(str(R.string.action_confirm))
    waitForText(str(R.string.accounts_empty))
}

fun ComposeTestRule.addManually(issuer: String, account: String, secret: String, hotp: Boolean = false) {
    click(str(R.string.accounts_add))
    click(str(R.string.add_manual))
    if (hotp) click("HOTP")
    type(str(R.string.manual_issuer), issuer)
    type(str(R.string.manual_account), account)
    type(str(R.string.manual_secret), secret)
    click(str(R.string.manual_next))
    waitForText(str(R.string.confirm_title))
    click(str(R.string.confirm_add))
    waitUntilGone(str(R.string.confirm_title))
}

fun clipboardText(): String? {
    var text: String? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    }
    return text
}

private fun SemanticsNodeInteraction.scrollIfPossible(): SemanticsNodeInteraction = apply {
    try {
        performScrollTo()
    } catch (ignored: AssertionError) {
        // Not inside a scrollable container (e.g. the floating add button).
    }
}
