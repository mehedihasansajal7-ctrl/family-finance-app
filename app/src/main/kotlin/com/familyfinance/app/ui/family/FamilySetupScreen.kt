package com.familyfinance.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyfinance.app.domain.model.UserProfile
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.InlineMessage
import com.familyfinance.app.ui.components.PlainTextField
import com.familyfinance.app.ui.components.SegmentedSelector
import com.familyfinance.app.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class FamilySetupMode(val label: String) {
    CREATE("Create a family"),
    JOIN("Join a family"),
}

@HiltViewModel
class FamilySetupViewModel @Inject constructor(
    private val families: com.familyfinance.app.domain.repository.FamilyRepository,
    private val profile: com.familyfinance.app.domain.repository.ProfileRepository,
) : BaseViewModel() {

    var mode by mutableStateOf(FamilySetupMode.CREATE)
        private set
    var familyName by mutableStateOf("")
        private set
    var inviteCode by mutableStateOf("")
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set

    fun onMode(value: FamilySetupMode) {
        mode = value
        message = null
    }

    fun onFamilyName(value: String) { familyName = value }
    fun onInviteCode(value: String) { inviteCode = value.uppercase() }

    fun submit(user: UserProfile) {
        message = null
        when (mode) {
            FamilySetupMode.CREATE -> {
                if (familyName.isBlank()) {
                    isError = true
                    message = "Enter a name for your family."
                    return
                }
                run(onSuccess = { result ->
                    val family = result as? com.familyfinance.app.domain.model.Family
                    isError = false
                    message = "Created \"${family?.name.orEmpty()}\". You are the owner."
                }) { families.createFamily(familyName.trim(), user.uid) }
            }

            FamilySetupMode.JOIN -> {
                if (inviteCode.isBlank()) {
                    isError = true
                    message = "Enter the invitation code you were given."
                    return
                }
                run(onSuccess = { result ->
                    val family = result as? com.familyfinance.app.domain.model.Family
                    isError = false
                    message = "Joined \"${family?.name.orEmpty()}\"."
                }) { families.redeemInvitation(inviteCode.trim(), user.uid) }
            }
        }
    }
}

@Composable
fun FamilySetupScreen(
    user: UserProfile,
    viewModel: FamilySetupViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val busy by viewModel.busy.collectAsState()
    ObserveMessages(viewModel, host)

    Scaffold(snackbarHost = { SnackbarHost(host) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Icon(
                Icons.Default.GroupAdd,
                contentDescription = null,
                modifier = Modifier.height(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Set up your family", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Hi ${user.displayName.ifBlank { user.email }}. Create a new family workspace, or join one " +
                    "with an invitation code. Your financial records stay private either way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            SegmentedSelector(
                options = FamilySetupMode.entries.toList(),
                selected = viewModel.mode,
                optionLabel = { it.label },
                onSelect = viewModel::onMode,
            )

            when (viewModel.mode) {
                FamilySetupMode.CREATE -> {
                    SectionCard(title = "New family", subtitle = "You will be the owner.") {
                        PlainTextField(
                            value = viewModel.familyName,
                            onValueChange = viewModel::onFamilyName,
                            label = "Family name",
                            supportingText = "e.g. Hasan Family",
                        )
                        Text(
                            "Currency BDT · Timezone Asia/Dhaka",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                FamilySetupMode.JOIN -> {
                    SectionCard(
                        title = "Join with a code",
                        subtitle = "Codes expire and can be revoked by the family owner.",
                    ) {
                        PlainTextField(
                            value = viewModel.inviteCode,
                            onValueChange = viewModel::onInviteCode,
                            label = "Invitation code",
                            supportingText = "Format: ABCD-1234",
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Text(
                                "Joining shares only your name and the family dashboard totals. " +
                                    "Your accounts, income and expenses stay private to you.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            InlineMessage(
                message = viewModel.message.orEmpty(),
                isError = viewModel.isError,
            )

            Button(
                onClick = { viewModel.submit(user) },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    when (viewModel.mode) {
                        FamilySetupMode.CREATE -> "Create family"
                        FamilySetupMode.JOIN -> "Join family"
                    }
                )
            }

            OutlinedButton(
                onClick = { viewModel.onMode(FamilySetupMode.CREATE) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Not sure? Create a family — you can invite others later.")
            }
        }
    }
}
