package io.github.yozedens.secureauth

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yozedens.secureauth.feature.accounts.AccountListViewModel
import io.github.yozedens.secureauth.feature.addaccount.AddAccountViewModel
import io.github.yozedens.secureauth.feature.edit.EditAccountViewModel
import io.github.yozedens.secureauth.feature.root.AppViewModel
import io.github.yozedens.secureauth.feature.root.RootScreen
import io.github.yozedens.secureauth.feature.root.UnlockedViewModels
import io.github.yozedens.secureauth.ui.theme.SecureAuthTheme

/**
 * Single activity. Extends [FragmentActivity] because BiometricPrompt requires it (design §31).
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App-wide, before any content is drawn: blocks screenshots, screen recording and
        // recents thumbnails (design §38). Compose dialogs inherit it; never override.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        val container = (application as SecureAuthApplication).container
        setContent {
            SecureAuthTheme {
                val viewModel: AppViewModel = viewModel(
                    factory = viewModelFactory { initializer { AppViewModel(container) } },
                )
                val accountListViewModel: AccountListViewModel = viewModel(
                    factory = viewModelFactory { initializer { AccountListViewModel(container) } },
                )
                val addAccountViewModel: AddAccountViewModel = viewModel(
                    factory = viewModelFactory { initializer { AddAccountViewModel(container) } },
                )
                val editAccountViewModel: EditAccountViewModel = viewModel(
                    factory = viewModelFactory { initializer { EditAccountViewModel(container) } },
                )
                RootScreen(
                    viewModel = viewModel,
                    viewModels = UnlockedViewModels(accountListViewModel, addAccountViewModel, editAccountViewModel),
                    biometric = container.biometric,
                )
            }
        }
    }
}
