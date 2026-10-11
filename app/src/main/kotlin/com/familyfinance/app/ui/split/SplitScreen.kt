package com.familyfinance.app.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyFormat
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.FamilyExpenseSplit
import com.familyfinance.app.domain.model.FamilyMemberView
import com.familyfinance.app.domain.model.FamilySettlement
import com.familyfinance.app.domain.model.MemberBalance
import com.familyfinance.app.domain.model.SettlementSuggestion
import com.familyfinance.app.domain.repository.FamilyRepository
import com.familyfinance.app.domain.repository.SplitRepository
import com.familyfinance.app.domain.usecase.SplitBalanceCalculator
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.MoneyField
import com.familyfinance.app.ui.components.PlainTextField
import com.familyfinance.app.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SplitViewModel @Inject constructor(
    private val splits: SplitRepository,
    private val families: FamilyRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    private val familyId = session.currentUser
        .map { it?.familyId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members: StateFlow<List<FamilyMemberView>> = familyId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else families.observeMembers(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val splitsFlow: StateFlow<List<FamilyExpenseSplit>> = familyId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else splits.observeSplits(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val settlementsFlow: StateFlow<List<FamilySettlement>> = familyId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else splits.observeSettlements(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val balances: StateFlow<List<MemberBalance>> = combine(splitsFlow, settlementsFlow) { s, st ->
        SplitBalanceCalculator.balances(s, st)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestions: StateFlow<List<SettlementSuggestion>> = combine(splitsFlow, settlementsFlow) { s, st ->
        SplitBalanceCalculator.suggestions(s, st)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<FamilyExpenseSplit>> = splitsFlow

    val currentUid: StateFlow<String?> = session.currentUser
        .map { it?.uid }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addSplit(title: String, totalRaw: String, paidByUid: String, participantUids: List<String>) {
        val family = familyId.value ?: return
        val total = Money.parseOrNull(totalRaw)
        if (title.isBlank() || total == null || !total.isPositive) {
            error("Enter a name and an amount greater than zero.")
            return
        }
        if (participantUids.size < 2) {
            error("Choose at least two participants.")
            return
        }
        val shares = equalShares(total, paidByUid, participantUids)
        val split = FamilyExpenseSplit(
            id = Ids.newId(),
            familyId = family,
            title = title.trim(),
            total = total,
            paidByUid = paidByUid,
            shares = shares,
            dateEpochDay = AppTime.todayEpochDay(),
            createdByUid = currentUid.value.orEmpty(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        run(onSuccess = { info("Split added.") }) { splits.addSplit(split) }
    }

    fun deleteSplit(split: FamilyExpenseSplit) {
        run(showBusy = false) { splits.deleteSplit(split.familyId, split.id) }
    }

    fun settle(suggestion: SettlementSuggestion) {
        val family = familyId.value ?: return
        val settlement = FamilySettlement(
            id = Ids.newId(),
            familyId = family,
            fromUid = suggestion.fromUid,
            toUid = suggestion.toUid,
            amount = suggestion.amount,
            dateEpochDay = AppTime.todayEpochDay(),
            createdAt = System.currentTimeMillis(),
        )
        run(onSuccess = { info("Settlement recorded.") }) { splits.addSettlement(settlement) }
    }

    /** Split equally; any remainder of integer division is added to the payer's own share. */
    private fun equalShares(total: Money, payer: String, participants: List<String>): Map<String, Money> {
        val n = participants.size.toLong()
        val base = total.minor / n
        val remainder = total.minor % n
        return participants.associateWith { uid ->
            Money.ofMinor(if (uid == payer) base + remainder else base)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(
    onBack: () -> Unit,
    viewModel: SplitViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val members by viewModel.members.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val history by viewModel.history.collectAsState()
    val currentUid by viewModel.currentUid.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    ObserveMessages(viewModel, host)

    fun name(uid: String): String =
        members.firstOrNull { it.uid == uid }?.fullName ?: "Member"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split & settle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add expense") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Balances", subtitle = "Positive means the family owes that member.") {
                    if (balances.isEmpty()) {
                        Text("No shared expenses yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        balances.forEach { balance ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(name(balance.uid), style = MaterialTheme.typography.bodyLarge)
                                val prefix = if (balance.net.isNegative) "owes " else "gets back "
                                Text(
                                    prefix + MoneyFormat.bdt(
                                        if (balance.net.isNegative) Money.ofMinor(-balance.net.minor) else balance.net,
                                    ),
                                    color = if (balance.net.isNegative) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                item {
                    SectionCard(title = "Settle up", subtitle = "The fewest payments that clear every balance.") {
                        suggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${name(suggestion.fromUid)} → ${name(suggestion.toUid)}  " +
                                        MoneyFormat.bdt(suggestion.amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.settle(suggestion) }) { Text("Settle") }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Shared expenses") {
                    if (history.isEmpty()) {
                        Text("Add an expense to split it between members.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            items(history, key = { it.id }) { split ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(split.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${name(split.paidByUid)} paid ${MoneyFormat.bdt(split.total)} · " +
                                    "${split.shares.size} people",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (split.createdByUid == currentUid) {
                            IconButton(onClick = { viewModel.deleteSplit(split) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete split")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSplitDialog(
            members = members,
            currentUid = currentUid,
            onDismiss = { showAdd = false },
            onSave = { title, amount, payer, participants ->
                viewModel.addSplit(title, amount, payer, participants)
                showAdd = false
            },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddSplitDialog(
    members: List<FamilyMemberView>,
    currentUid: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<String>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var payer by remember { mutableStateOf(currentUid ?: members.firstOrNull()?.uid.orEmpty()) }
    var participants by remember { mutableStateOf(members.map { it.uid }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add shared expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainTextField(value = title, onValueChange = { title = it }, label = "What was it for?")
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Total amount")
                Text("Paid by", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    members.forEach { member ->
                        FilterChip(
                            selected = payer == member.uid,
                            onClick = { payer = member.uid },
                            label = { Text(member.fullName) },
                        )
                    }
                }
                Text("Split between", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    members.forEach { member ->
                        FilterChip(
                            selected = member.uid in participants,
                            onClick = {
                                participants = if (member.uid in participants) {
                                    participants - member.uid
                                } else {
                                    participants + member.uid
                                }
                            },
                            label = { Text(member.fullName) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, amount, payer, participants.toList()) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
