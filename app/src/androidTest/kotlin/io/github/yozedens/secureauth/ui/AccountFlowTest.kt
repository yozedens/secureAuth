package io.github.yozedens.secureauth.ui

import android.Manifest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.github.yozedens.secureauth.MainActivity
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.core.model.OtpKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The three UI paths of design §50.8, end to end on a device with the real Keystore and
 * DataStore, and fakes for the clock, biometrics and camera frames.
 *
 * Test key: RFC 4226 / RFC 6238 seed "12345678901234567890" (Base32 below).
 */
@RunWith(AndroidJUnit4::class)
class AccountFlowTest {

    private val app = FreshAppRule()
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.CAMERA))
        .around(app)
        .around(compose)

    @Test
    fun firstLaunchAddCopyEditDelete() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onboard(PIN)
            compose.addManually(ISSUER, ACCOUNT, GROUPED_SECRET)

            // RFC 6238 at T = 59 s, 6 digits, grouped for display.
            compose.waitForText("287 082")
            compose.click(str(R.string.accounts_copy))
            compose.waitForText(str(R.string.accounts_copied))
            compose.awaitClipboard("287082")

            compose.click(str(R.string.accounts_edit))
            compose.replace(str(R.string.manual_issuer), "RenamedCorp")
            compose.click(str(R.string.edit_save))
            compose.waitForText(str(R.string.edit_saved))
            compose.click(str(R.string.settings_back))
            compose.waitForText("RenamedCorp")

            SecretLeakCheck.assertNoPlaintextOnDisk(ISSUER, "RenamedCorp", ACCOUNT, SECRET, SEED)
            SecretLeakCheck.assertNotInLogcat(SECRET, GROUPED_SECRET, SEED)

            compose.click(str(R.string.accounts_edit))
            compose.click(str(R.string.edit_delete))
            compose.click(str(R.string.delete_confirm))
            compose.waitForText(str(R.string.accounts_empty))
        }
    }

    @Test
    fun biometricUnlockThenScanConfirmSave() {
        app.scanner.nextCode = "otpauth://totp/$ISSUER:bob@example.com?secret=$SECRET&issuer=$ISSUER"
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onboard(PIN)

            // Enabling biometrics requires one successful prompt first.
            compose.click(str(R.string.home_settings))
            compose.toggle()
            compose.await("first biometric prompt") { app.biometric.prompts.get() == 1 }
            compose.waitFor(isToggleable() and isOn())
            compose.click(str(R.string.settings_back))

            // Locking shows the lock screen, which prompts on its own; the fake succeeds.
            compose.click(str(R.string.home_lock))
            compose.await("biometric prompt on the lock screen") { app.biometric.prompts.get() >= 2 }
            compose.waitForText(str(R.string.accounts_empty))

            compose.click(str(R.string.accounts_add))
            compose.click(str(R.string.add_scan))
            compose.waitForText(str(R.string.confirm_title))
            compose.waitFor(hasSetTextAction() and hasText("bob@example.com"))
            compose.click(str(R.string.confirm_add))
            compose.waitForText("287 082")
        }
    }

    @Test
    fun hotpGeneratePersistsCounterAndCopyDoesNotIncrement() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onboard(PIN)
            compose.addManually(ISSUER, ACCOUNT, SECRET, hotp = true)
            compose.waitForText(str(R.string.accounts_counter, 0))
            compose.waitForText(str(R.string.accounts_hotp_hidden))

            // RFC 4226 appendix D: counter 0 -> 755224, counter 1 -> 287082.
            compose.click(str(R.string.accounts_generate))
            compose.waitForText("755 224")
            compose.waitForText(str(R.string.accounts_counter, 1))

            compose.click(str(R.string.accounts_copy))
            compose.waitForText(str(R.string.accounts_copied))
            compose.awaitClipboard("755224")
            compose.waitForText(str(R.string.accounts_counter, 1))
            assertEquals(OtpKind.Hotp(1), app.container.vault.accounts.value.single().kind)

            compose.click(str(R.string.accounts_generate))
            compose.waitForText("287 082")
            compose.waitForText(str(R.string.accounts_counter, 2))
        }
    }

    private companion object {
        const val PIN = "246813"
        const val ISSUER = "ExampleCorp"
        const val ACCOUNT = "alice@example.com"
        const val SEED = "12345678901234567890"
        const val SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        const val GROUPED_SECRET = "GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ"
    }
}
