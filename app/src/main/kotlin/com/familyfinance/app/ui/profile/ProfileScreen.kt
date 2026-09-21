package com.familyfinance.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.MemberProfile
import com.familyfinance.app.domain.repository.AuditRepository
import com.familyfinance.app.domain.repository.ProfileRepository
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.DateField
import com.familyfinance.app.ui.components.PlainTextField
import com.familyfinance.app.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val audit: AuditRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    var fullName by mutableStateOf("")
        private set
    var mobile by mutableStateOf("")
        private set
    var relationship by mutableStateOf("")
        private set
    var occupation by mutableStateOf("")
        private set
    var notes by mutableStateOf("")
        private set
    var dateOfBirth by mutableStateOf<Long?>(null)
        private set
    var shareProfile by mutableStateOf(true)
        private set

    private val _loaded = MutableStateFlow<MemberProfile?>(null)
    val loaded: StateFlow<MemberProfile?> = _loaded.asStateFlow()

    init {
        launchSafe {
            val uid = session.currentUser.filterNotNull().first().uid
            profiles.observeProfile(uid).collect { profile ->
                if (profile != null && _loaded.value?.updatedAt != profile.updatedAt) {
                    _loaded.value = profile
                    fullName = profile.fullName
                    mobile = profile.mobile.orEmpty()
                    relationship = profile.relationship.orEmpty()
                    occupation = profile.occupation.orEmpty()
                    notes = profile.notes.orEmpty()
                    dateOfBirth = profile.dateOfBirthEpochDay
                    shareProfile = profile.shareProfileWithFamily
                }
            }
        }
    }

    fun onFullName(value: String) { fullName = value }
    fun onMobile(value: String) { mobile = value.filter { it.isDigit() || it == '+' }.take(15) }
    fun onRelationship(value: String) { relationship = value }
    fun onOccupation(value: String) { occupation = value }
    fun onNotes(value: String) { notes = value }
    fun onDateOfBirth(value: Long?) { dateOfBirth = value }
    fun onShareProfile(value: Boolean) { shareProfile = value }

    fun save() {
        val existing = _loaded.value
        if (existing == null) {
            error("Your profile is still loading.")
            return
        }
        if (fullName.isBlank()) {
            error("Your name cannot be empty.")
            return
        }
        run(onSuccess = {
            info("Profile updated.")
            launchSafe {
                audit.record(
                    familyId = existing.familyId,
                    actorUid = existing.uid,
                    action = AuditRepository.ACTION_PROFILE_UPDATED,
                )
            }
        }) {
            profiles.updateProfile(
                existing.copy(
                    fullName = fullName.trim(),
                    mobile = mobile.takeIf { it.isNotBlank() },
                    relationship = relationship.trim().takeIf { it.isNotBlank() },
                    occupation = occupation.trim().takeIf { it.isNotBlank() },
                    notes = notes.trim().takeIf { it.isNotBlank() },
                    dateOfBirthEpochDay = dateOfBirth,
                    shareProfileWithFamily = shareProfile,
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val busy by viewModel.busy.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    ObserveMessages(viewModel, host)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Basic information") {
                PlainTextField(value = viewModel.fullName, onValueChange = viewModel::onFullName, label = "Full name")
                PlainTextField(
                    value = viewModel.mobile,
                    onValueChange = viewModel::onMobile,
                    label = "Mobile number",
                    keyboardType = KeyboardType.Phone,
                )
                PlainTextField(
                    value = viewModel.relationship,
                    onValueChange = viewModel::onRelationship,
                    label = "Family relationship",
                    supportingText = "e.g. Father, Mother, Son",
                )
                PlainTextField(
                    value = viewModel.occupation,
                    onValueChange = viewModel::onOccupation,
                    label = "Occupation",
                )
                val dob = viewModel.dateOfBirth
                if (dob != null) {
                    DateField(label = "Date of birth", epochDay = dob, onChange = viewModel::onDateOfBirth)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (dob == null) "Add date of birth" else "Remove date of birth",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.onDateOfBirth(if (dob == null) AppTime.today().minusYears(30).toEpochDay() else null)
                        },
                    ) { Text(if (dob == null) "Add" else "Remove") }
                }
                PlainTextField(
                    value = viewModel.notes,
                    onValueChange = viewModel::onNotes,
                    label = "Notes (optional)",
                    singleLine = false,
                )
            }

            SectionCard(
                title = "Privacy",
                subtitle = "Financial data is always private, regardless of this setting.",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Share my name and photo with family", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Only your name, photo and relationship are ever shared. Never your money.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = viewModel.shareProfile, onCheckedChange = viewModel::onShareProfile)
                }
            }

            SectionCard(title = "Account") {
                Text(
                    "Email: ${loaded?.email ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Every member has their own login. There is no shared family account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = !busy && loaded != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Save profile")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
