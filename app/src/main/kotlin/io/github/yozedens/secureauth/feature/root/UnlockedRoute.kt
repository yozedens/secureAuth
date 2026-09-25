package io.github.yozedens.secureauth.feature.root

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yozedens.secureauth.feature.accounts.AccountListScreen
import io.github.yozedens.secureauth.feature.accounts.AccountListViewModel
import io.github.yozedens.secureauth.feature.addaccount.AddAccountViewModel
import io.github.yozedens.secureauth.feature.addaccount.AddMenuScreen
import io.github.yozedens.secureauth.feature.addaccount.ConfirmAccountScreen
import io.github.yozedens.secureauth.feature.addaccount.ManualEntryScreen
import io.github.yozedens.secureauth.feature.addaccount.message
import io.github.yozedens.secureauth.feature.edit.EditAccountScreen
import io.github.yozedens.secureauth.feature.edit.EditAccountViewModel
import io.github.yozedens.secureauth.feature.scanner.CodeScanner
import io.github.yozedens.secureauth.feature.scanner.ScanScreen
import kotlinx.coroutines.launch

/**
 * The add / edit pages behind the lock gate (design §43–§46). Pending data lives in the
 * view-models' memory only; pages carry at most an account id, never a secret.
 */
@Composable
fun AddFlowRoute(page: Page, viewModel: AppViewModel, addViewModel: AddAccountViewModel, scanner: CodeScanner) {
    val add by addViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val leave = {
        addViewModel.clear()
        viewModel.navigate(Page.Home)
    }
    BackHandler {
        if (page == Page.AddMenu) leave() else viewModel.back()
    }
    when (page) {
        Page.AddMenu -> AddMenuScreen(
            viewModel = addViewModel,
            problem = add.scanProblem,
            onScan = {
                addViewModel.clearScanProblem()
                viewModel.navigate(Page.Scan)
            },
            onManual = { viewModel.navigate(Page.ManualEntry) },
            onConfirm = { viewModel.navigate(Page.Confirm) },
            onBack = leave,
        )
        Page.Scan -> ScanScreen(
            scanner = scanner,
            problemText = add.scanProblem?.let { stringResource(it.message()) },
            onResult = { text -> scope.launch { if (addViewModel.onScanned(text)) viewModel.navigate(Page.Confirm) } },
            onCancel = viewModel::back,
        )
        Page.ManualEntry -> ManualEntryScreen(
            viewModel = addViewModel,
            form = add.manual,
            onValid = { viewModel.navigate(Page.Confirm) },
            onBack = viewModel::back,
        )
        // No pending account only transiently: right after confirm / cancel, before the
        // navigation that follows arrives, or while locked (unlocking returns to Home).
        // Redirecting from here would race that navigation, so render nothing.
        Page.Confirm -> add.pending?.let { pending ->
            ConfirmAccountScreen(
                viewModel = addViewModel,
                pending = pending,
                busy = add.busy,
                onAdded = { viewModel.navigate(Page.Home) },
                onCancel = leave,
            )
        }
        else -> Unit
    }
}

@Composable
fun EditRoute(viewModel: AppViewModel, editViewModel: EditAccountViewModel) {
    val form by editViewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = viewModel::back)
    // Empty only transiently (after delete, or while locked); see Page.Confirm above.
    if (form.accountId == null) return
    EditAccountScreen(
        viewModel = editViewModel,
        form = form,
        onDeleted = { viewModel.navigate(Page.Home) },
        onBack = viewModel::back,
    )
}

@Composable
fun HomeRoute(
    viewModel: AppViewModel,
    accountListViewModel: AccountListViewModel,
    editViewModel: EditAccountViewModel,
) {
    AccountListScreen(
        viewModel = accountListViewModel,
        onOpenSettings = { viewModel.navigate(Page.Settings) },
        onLockNow = viewModel::lockNow,
        onAdd = { viewModel.navigate(Page.AddMenu) },
        onEdit = { id ->
            editViewModel.load(id)
            viewModel.navigate(Page.Edit(id))
        },
    )
}
