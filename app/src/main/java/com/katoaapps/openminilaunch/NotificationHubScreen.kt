package com.katoaapps.openminilaunch

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun NotificationHubScreen(actions: DeviceActions, goBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    var accessGranted by remember { mutableStateOf(NotificationHub.hasAccess(context)) }
    var showAccessDisclosure by remember { mutableStateOf(false) }
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    val conversations = NotificationHub.conversations()
    val selectedConversation = conversations.firstOrNull { it.id == selectedConversationId }

    DisposableEffect(activity, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessGranted = NotificationHub.hasAccess(context)
                if (accessGranted) NotificationHub.requestReconnect(context)
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = selectedConversation != null) { selectedConversationId = null }

    if (selectedConversation != null) {
        ConversationWindow(
            conversation = selectedConversation,
            actions = actions,
            goBack = { selectedConversationId = null },
        )
    } else {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PageHeader("Conversations", goBack)
            when {
                !accessGranted -> ConversationAccessEmptyState { showAccessDisclosure = true }
                conversations.isEmpty() -> NoConversationsEmptyState()
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "ACTIVE CONVERSATIONS",
                            Modifier.padding(top = 14.dp, bottom = 2.dp),
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                        )
                    }
                    items(conversations, key = HubConversation::id) { conversation ->
                        ConversationCard(conversation, actions) { selectedConversationId = conversation.id }
                    }
                    item { Spacer(Modifier.height(28.dp)) }
                }
            }
        }
    }

    if (showAccessDisclosure) {
        NotificationAccessDisclosureDialog(
            onContinue = {
                showAccessDisclosure = false
                actions.openNotificationAccessSettings()
            },
            onDismiss = { showAccessDisclosure = false },
        )
    }
}

@Composable
private fun ConversationAccessEmptyState(onEnable: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.NotificationsOff, null, Modifier.size(54.dp), tint = Rust)
        Text("Conversation access is off", fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp))
        Text(
            "MinkLauncher Open uses Android notification access only to show active conversations and send replies through their messaging apps. Contents stay on this device and are never sent to Katoa Apps.",
            color = Muted,
            modifier = Modifier.padding(vertical = 14.dp),
        )
        Button(onClick = onEnable) { Text("Open notification access") }
    }
}

@Composable
private fun NoConversationsEmptyState() {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Forum, null, Modifier.size(54.dp), tint = Sage)
        Text("No active conversations", fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp))
        Text("New message conversations will appear here.", color = Muted)
    }
}

@Composable
private fun ConversationCard(conversation: HubConversation, actions: DeviceActions, onOpen: () -> Unit) {
    val latest = conversation.latestMessage
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ConversationSourceIcons(conversation.sourcePackages, actions)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(latest?.timestamp ?: conversation.latestNotification.postedAt)),
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    latest?.text.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (conversation.messages.size > 1) {
                    Text("${conversation.messages.size} messages", color = Rust, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ConversationSourceIcons(packages: List<String>, actions: DeviceActions) {
    Box(Modifier.size(if (packages.size > 1) 46.dp else 38.dp)) {
        packages.take(3).forEachIndexed { index, packageName ->
            Box(
                Modifier.align(
                    when (index) {
                        0 -> Alignment.TopStart
                        1 -> Alignment.Center
                        else -> Alignment.BottomEnd
                    },
                )
                    .background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp),
            ) {
                AppIcon(packageName, actions, if (index == 0) 34.dp else 22.dp)
            }
        }
    }
}

@Composable
private fun ConversationWindow(conversation: HubConversation, actions: DeviceActions, goBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    val scope = rememberCoroutineScope()
    var replyText by remember(conversation.id) { mutableStateOf("") }
    var replyStatus by remember(conversation.id) { mutableStateOf<String?>(null) }
    val replyTarget = conversation.replyTarget
    val messageListState = rememberLazyListState()

    fun sendReply() {
        val target = replyTarget ?: return
        if (NotificationHub.reply(target, replyText)) {
            replyText = ""
            replyStatus = "Sent"
        } else {
            replyStatus = "Reply unavailable"
        }
    }

    LaunchedEffect(replyText) {
        if (replyText.isNotBlank()) replyStatus = null
    }
    LaunchedEffect(conversation.messages.size) {
        if (conversation.messages.isNotEmpty()) messageListState.scrollToItem(conversation.messages.size + 1)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        PageHeader(conversation.name, goBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConversationSourceIcons(conversation.sourcePackages, actions)
            Text(
                conversation.notifications.map(HubNotification::appName).distinct().joinToString(" · "),
                Modifier.weight(1f).padding(horizontal = 10.dp),
                color = Muted,
                fontSize = 12.sp,
                maxLines = 2,
            )
            OutlinedButton(
                onClick = {
                    val target = conversation.openTarget
                    if (!NotificationHub.open(context, target)) {
                        if (!actions.launchPackage(target.packageName)) {
                            Toast.makeText(context, "Could not open ${target.appName}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        scope.launch {
                            delay(500)
                            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                                !actions.launchPackage(target.packageName)
                            ) {
                                Toast.makeText(context, "Could not open ${target.appName}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(17.dp))
                Text("View full conversation", Modifier.padding(start = 6.dp))
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
            state = messageListState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(conversation.messages, key = ConversationMessage::id) { message ->
                ConversationBubble(message, showSource = conversation.sourcePackages.size > 1, actions = actions)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
            replyStatus?.let { status ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (status == "Sent") Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Sage)
                    Text(if (status == "Sent") "Sent" else status, color = if (status == "Sent") Sage else Rust, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (replyTarget != null) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Reply") },
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (replyText.isNotBlank()) sendReply() }),
                    trailingIcon = {
                        FilledIconButton(onClick = ::sendReply, enabled = replyText.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Reply, "Send reply")
                        }
                    },
                )
            } else {
                Text("This conversation does not provide an inline reply action.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConversationBubble(message: ConversationMessage, showSource: Boolean, actions: DeviceActions) {
    val bubbleColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val bubbleContentColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        CompositionLocalProvider(LocalContentColor provides bubbleContentColor) {
            Column(
                Modifier.fillMaxWidth(.82f)
                    .background(bubbleColor, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (!message.isOutgoing && !message.senderName.isNullOrBlank()) {
                    Text(message.senderName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(message.text, modifier = Modifier.padding(top = if (message.senderName.isNullOrBlank()) 0.dp else 2.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 5.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showSource) {
                        AppIcon(message.packageName, actions, 14.dp)
                        Text(
                            message.appName,
                            color = bubbleContentColor.copy(alpha = .68f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp)),
                        color = bubbleContentColor.copy(alpha = .68f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
