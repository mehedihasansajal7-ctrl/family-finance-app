package com.familyfinance.app.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.FamilyMemberView
import com.familyfinance.app.domain.model.FamilyInvitation
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.UserProfile
import com.familyfinance.app.domain.repository.AuditRepository
import com.familyfinance.app.domain.repository.FamilyRepository
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.InlineMessage
import com.familyfinance.app.ui.components.PickerField
import com.familyfinance.app.ui.components.PlainTextField
import com.familyfinance.app.ui.components.SectionCard
import com.familyfinance.app.ui.components.Tag
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val families: FamilyRepository,
    private val audit: AuditRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    val members: StateFlow<List<FamilyMemberView>> = session.currentUser
        .map { it?.familyId }
        .distinctUntilChanged()
        .flatMapLatest { familyId ->
            if (familyId.isNullOrBlank()) flowOf(emptyList()) else families.observeMembers(familyId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _invitation = MutableStateFlow<FamilyInvitation?>(null)
    val invitation: StateFlow<FamilyInvitation?> = _invitation

    val canManage: StateFlow<Boolean> = session.currentUser
        .map { it?.role?.canManageFamily == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isOwner: StateFlow<Boolean> = session.currentUser
        .map { it?.role == Role.OWNER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun createInvitation(invitedEmail: String, validHours: Int) {
        run(onSuccess = { result ->
            val invitation = result as? FamilyInvitation
            _invitation.value = invitation
            info("Invitation code created. Share it securely.")
            launchSafe {
                val user = session.currentUserOnce()
                if (user != null && invitation != null) {
                    audit.record(
                        familyId = user.familyId,
                        actorUid = user.uid,
                        action = AuditRepository.ACTION_INVITATION_CREATED,
                        context = mapOf("code" to invitation.code, "expiresAt" to invitation.expiresAt.toString()),
                    )
                }
            }
        }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            families.createInvitation(
                familyId = user.familyId.orEmpty(),
                createdByUid = user.uid,
                invitedEmail = invitedEmail.takeIf { it.isNotBlank() },
                validForHours = validHours,
            )
        }
    }

    fun revoke(invitation: FamilyInvitation) {
        run(onSuccess = {
            _invitation.value = null
            info("Invitation revoked.")
        }) { families.revokeInvitation(invitation.id) }
    }

    fun changeRole(member: FamilyMemberView, role: Role) {
        run(onSuccess = { info("${member.fullName} is now ${role.label}.") }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            families.updateRole(user.familyId.orEmpty(), member.uid, role)
        }
    }

    fun remove(member: FamilyMemberView) {
        run(onSuccess = { info("${member.fullName} was removed from the family.") }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            families.removeMember(user.familyId.orEmpty(), member.uid)
        }
    }

    fun clearInvitation() { _invitation.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    onBack: () -> Unit,
    currentUser: UserProfile?,
    viewModel: MembersViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val members by viewModel.members.collectAsState()
    val invitation by viewModel.invitation.collectAsState()
    val canManage by viewModel.canManage.collectAsState()
    val isOwner by viewModel.isOwner.collectAsState()
    val clipboard = LocalClipboardManager.current

    ObserveMessages(viewModel, host)

    var showInvite by remember { mutableStateOf(false) }
    var roleTarget by remember { mutableStateOf<FamilyMemberView?>(null) }
    var removeTarget by remember { mutableStateOf<FamilyMemberView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family members") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = { showInvite = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Invite a member")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Members see the shared family dashboard. Nobody — not even the owner — can see " +
                        "another member's accounts, income, expenses or balances.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            invitation?.let { active ->
                item {
                    SectionCard(title = "Invitation code", subtitle = "Single use · expires ${AppTime.formatEpochDay(0)}") {
                        Text(
                            active.code,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Expires ${AppTime.format(AppTime.fromEpochDay((active.expiresAt / 86_400_000L)))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(active.code))
                            }) { Text("Copy code") }
                            TextButton(onClick = { viewModel.revoke(active) }) { Text("Revoke") }
                        }
                        Text(
                            "Send this code to the person you are inviting. It can only be used once and can " +
                                "be revoked at any time. No financial data is shared during joining.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(members, key = { it.uid }) { member ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.fullName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                member.profile.relationship ?: member.profile.email.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tag(member.role.label)
                                if (!member.isActive) Tag("Inactive")
                                if (member.uid == currentUser?.uid) Tag("You")
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (isOwner && member.uid != currentUser?.uid) {
                                TextButton(onClick = { roleTarget = member }) { Text("Role") }
                            }
                            if (canManage && member.uid != currentUser?.uid) {
                                TextButton(onClick = { removeTarget = member }) { Text("Remove") }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showInvite) {
        InviteDialog(
            onDismiss = { showInvite = false },
            onInvite = { email, hours ->
                viewModel.createInvitation(email, hours)
                showInvite = false
            },
        )
    }

    roleTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { roleTarget = null },
            title = { Text("Change role for ${member.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField(
                        label = "Role",
                        options = listOf(Role.MEMBER, Role.ADMIN),
                        selected = member.role,
                        optionLabel = { it.label },
                        onSelect = {
                            viewModel.changeRole(member, it)
                            roleTarget = null
                        },
                    )
                    Text(
                        "Admins manage family settings, budgets and invitations. They still cannot see any " +
                            "member's private financial data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { roleTarget = null }) { Text("Close") } },
        )
    }

    removeTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${member.fullName}?") },
            text = {
                Text(
                    "They will lose access to the family dashboard. Their own financial records remain " +
                        "private to them and are not deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(member)
                    removeTarget = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InviteDialog(onDismiss: () -> Unit, onInvite: (String, Int) -> Unit) {
    var email by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("72") }
    var validation by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite a family member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Their email (optional)",
                    supportingText = "Used only as a note for you. The code is what they need.",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                )
                PlainTextField(
                    value = hours,
                    onValueChange = { hours = it.filter { c -> c.isDigit() }.take(4) },
                    label = "Valid for (hours)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                )
                Text(
                    "The person must sign in with their own account before they can join. They will never " +
                        "see your financial data, and you will never see theirs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InlineMessage(message = validation.orEmpty(), isError = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedHours = hours.toIntOrNull() ?: 72
                if (parsedHours !in 1..720) {
                    validation = "Choose between 1 and 720 hours."
                    return@TextButton
                }
                onInvite(email, parsedHours)
            }) { Text("Create code") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
