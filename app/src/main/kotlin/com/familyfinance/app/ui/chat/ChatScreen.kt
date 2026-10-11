package com.familyfinance.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.ChatReadState
import com.familyfinance.app.domain.model.FamilyChatMessage
import com.familyfinance.app.domain.repository.ChatRepository
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

private val QUICK_REACTIONS = listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F")

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val session: SessionManager,
) : BaseViewModel() {
    val chatMessages: StateFlow<List<FamilyChatMessage>> = session.currentUser
        .map { it?.familyId }
        .flatMapLatest { familyId -> if (familyId == null) emptyFlow() else chat.observeMessages(familyId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reads: StateFlow<List<ChatReadState>> = session.currentUser
        .map { it?.familyId }
        .flatMapLatest { familyId -> if (familyId == null) emptyFlow() else chat.observeReads(familyId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val familyId: StateFlow<String?> = session.currentUser
        .map { it?.familyId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentUid: StateFlow<String?> = session.currentUser
        .map { it?.uid }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun send(text: String, replyTo: FamilyChatMessage?, onSuccess: () -> Unit) {
        val family = familyId.value ?: return
        run(onSuccess = { onSuccess() }) { chat.sendMessage(family, text, replyTo) }
    }

    fun sendImage(fileName: String, mimeType: String, bytes: ByteArray) {
        val family = familyId.value ?: return
        run { chat.sendImageMessage(family, fileName, mimeType, bytes) }
    }

    fun react(message: FamilyChatMessage, emoji: String) {
        val family = familyId.value ?: return
        run(showBusy = false) { chat.toggleReaction(family, message.id, emoji) }
    }

    fun delete(message: FamilyChatMessage) {
        val family = familyId.value ?: return
        run(showBusy = false) { chat.deleteMessage(family, message.id) }
    }

    fun markRead() {
        val family = familyId.value ?: return
        launchSafe { chat.markRead(family) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.chatMessages.collectAsState()
    val reads by viewModel.reads.collectAsState()
    val currentUid by viewModel.currentUid.collectAsState()
    var draft by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<FamilyChatMessage?>(null) }
    var actionTarget by remember { mutableStateOf<FamilyChatMessage?>(null) }
    var zoomTarget by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val host = rememberMessageHost()
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "photo"
                viewModel.sendImage(name, mime, bytes)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(messages.size, currentUid) { viewModel.markRead() }
    ObserveMessages(viewModel, host)

    Scaffold(
        snackbarHost = { SnackbarHost(host) },
        topBar = {
            TopAppBar(
                title = { Text("Family chat") },
                navigationIcon = onBack?.let { back ->
                    {
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                } ?: {},
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                replyTarget?.let { target ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Replying", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    previewText(target),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                            IconButton(onClick = { replyTarget = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { photoPicker.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach photo")
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 1000) draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Write to your family…") },
                        maxLines = 4,
                    )
                    IconButton(
                        onClick = { viewModel.send(draft, replyTarget) { draft = ""; replyTarget = null } },
                        enabled = draft.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                    }
                }
            }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No messages yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Start a conversation with your family. Synced history stays available offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    val isMine = message.senderUid == currentUid
                    val seen = if (isMine) {
                        reads.any { it.uid != currentUid && it.lastReadAt >= message.createdAt }
                    } else {
                        false
                    }
                    ChatBubble(
                        message = message,
                        isMine = isMine,
                        seen = seen,
                        onLongPress = { actionTarget = message },
                        onPhotoTap = { message.attachmentUrl?.let { zoomTarget = it } },
                    )
                }
            }
        }
    }

    actionTarget?.let { message ->
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QUICK_REACTIONS.forEach { emoji ->
                        TextButton(onClick = {
                            viewModel.react(message, emoji)
                            actionTarget = null
                        }) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
                TextButton(onClick = {
                    replyTarget = message
                    actionTarget = null
                }) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                    Text("  Reply")
                }
                if (message.senderUid == currentUid) {
                    TextButton(onClick = {
                        viewModel.delete(message)
                        actionTarget = null
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("  Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    zoomTarget?.let { url ->
        Dialog(onDismissRequest = { zoomTarget = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun previewText(message: FamilyChatMessage): String =
    if (!message.attachmentUrl.isNullOrBlank() && message.text.isBlank()) "Photo" else message.text

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: FamilyChatMessage,
    isMine: Boolean,
    seen: Boolean,
    onLongPress: () -> Unit,
    onPhotoTap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.combinedClickable(onClick = onPhotoTap, onLongClick = onLongPress),
            colors = CardDefaults.cardColors(
                containerColor = if (isMine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)) {
                Text(
                    if (isMine) "You" else message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                message.replyPreview?.let { preview ->
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp, bottom = 4.dp)
                            .fillMaxWidth(),
                    )
                }
                if (message.attachmentUrl != null) {
                    AsyncImage(
                        model = message.attachmentUrl,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(top = 4.dp).size(width = 220.dp, height = 220.dp),
                    )
                }
                if (message.text.isNotBlank()) {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                }
                if (message.reactions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        message.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    if (count > 1) "$emoji $count" else emoji,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)) +
                        if (isMine && seen) " · Seen" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
