package com.iceflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iceflow.data.model.*
import com.iceflow.data.repository.RemoteRoutingRuleSetRepository
import com.iceflow.service.RemoteRuleSetScheduler
import com.iceflow.i18n.T
import com.iceflow.i18n.Tf
import com.iceflow.ui.theme.IceRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun RoutingRuleSetPage(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    repository: RemoteRoutingRuleSetRepository,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RemoteRoutingRuleSet?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val updateFailedText = T("route.updateFailed")
    val importFailedText = T("route.importFailed")
    val ruleSetTotalLimitText = T("route.ruleSetTotalLimit")
    LaunchedEffect(Unit) { while (true) { delay(5000); refresh++ } }
    BackHandler { if (busy == null) back() }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (busy == null) back() }) { Icon(Icons.Default.Close, T("common.close")) }
            Text(T("route.ruleSetsTitle"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { add = true }) { Icon(Icons.Default.Add, T("route.ruleSetAdd"), tint = MaterialTheme.colorScheme.primary) }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (settings.remoteRuleSets.isEmpty()) {
            Text(T("route.ruleSetEmpty"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(settings.remoteRuleSets, key = { _, set -> set.id }) { _, set ->
                val status = remember(refresh, set.id) { repository.status(set.id) }
                Surface(shape = RoundedCornerShape(IceRadius.row), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(set.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Switch(set.enabled, { enabled ->
                                val changedSets = settings.remoteRuleSets.map { if (it.id == set.id) it.copy(enabled = enabled) else it }
                                var withinLimit = true
                                if (enabled) {
                                    withinLimit = runCatching { repository.validateEnabledTotal(changedSets) }.isSuccess
                                    if (!withinLimit) {
                                        message = ruleSetTotalLimitText
                                    }
                                }
                                if (withinLimit) {
                                    busy = set.id
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { runCatching { repository.loadEnabled(changedSets) } }
                                        result.onSuccess { expanded ->
                                            update { current -> current.copy(remoteRuleSets = changedSets, expandedRemoteRules = expanded) }
                                            RemoteRuleSetScheduler.schedule(context, changedSets.first { it.id == set.id })
                                            message = null
                                        }.onFailure { message = it.message ?: updateFailedText }
                                        busy = null
                                    }
                                }
                            }, enabled = busy == null)
                        }
                        Text(set.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val updatedText = if (status.updatedAt > 0) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(status.updatedAt)) else T("route.ruleSetNever")
                        Text(updatedText + " · " + Tf("route.ruleSetCount", status.count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (status.error != null) Text(T("route.ruleSetError") + ": " + status.error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        else if (status.count == 0) Text(T("route.ruleSetNoCache"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = busy == null, onClick = {
                                busy = set.id
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching { repository.update(set, settings.remoteRuleSets) } }
                                    result.onSuccess {
                                        update { current -> current.copy(expandedRemoteRules = repository.loadEnabled(current.remoteRuleSets)) }
                                        message = null
                                    }.onFailure { message = it.message ?: updateFailedText }
                                    refresh++
                                    busy = null
                                }
                            }) {
                                if (busy == set.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text(if (busy == set.id) T("route.ruleSetUpdating") else T("route.ruleSetUpdateNow"))
                            }
                            IconButton(enabled = busy == null, onClick = { deleting = set }) { Icon(Icons.Default.Delete, T("common.delete"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
    if (add) RemoteRuleSetDialog(onDismiss = { add = false }) { set ->
        runCatching { set.validate() }.onSuccess {
            if (settings.remoteRuleSets.size >= 100 || settings.remoteRuleSets.any { it.id == set.id }) message = importFailedText
            else {
                update { it.copy(remoteRuleSets = it.remoteRuleSets + set) }
                RemoteRuleSetScheduler.schedule(context, set)
                add = false
                message = null
            }
        }.onFailure { message = it.message ?: importFailedText }
    }
    deleting?.let { set -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text(T("route.ruleSetDeleteTitle")) }, text = { Text(T("route.ruleSetDeleteBody")) },
        confirmButton = { TextButton({
            update { current ->
                val remaining = current.remoteRuleSets.filterNot { item -> item.id == set.id }
                current.copy(remoteRuleSets = remaining, expandedRemoteRules = repository.loadEnabled(remaining))
            }
            RemoteRuleSetScheduler.cancel(context, set.id); repository.delete(set.id); deleting = null; refresh++
        }) { Text(T("common.delete")) } },
        dismissButton = { TextButton({ deleting = null }) { Text(T("common.cancel")) } },
    ) }
}

@Composable private fun RemoteRuleSetDialog(onDismiss: () -> Unit, save: (RemoteRoutingRuleSet) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(RemoteRuleSetFormat.DOMAIN_LIST) }
    var action by remember { mutableStateOf(RouteAction.PROXY) }
    var interval by remember { mutableStateOf("24") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidMessage = T("route.importFailed")
    val formats = listOf(
        RemoteRuleSetFormat.DOMAIN_LIST to "route.ruleSetDomainList",
        RemoteRuleSetFormat.IP_LIST to "route.ruleSetIpList",
        RemoteRuleSetFormat.CLASH_CLASSICAL to "route.ruleSetClassical",
        RemoteRuleSetFormat.CLASH_DOMAIN to "route.ruleSetDomainYaml",
        RemoteRuleSetFormat.CLASH_IPCIDR to "route.ruleSetIpYaml",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T("route.ruleSetAdd")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                EditorInput(T("route.ruleSetName"), name, { name = it })
                EditorInput(T("route.ruleSetUrl"), url, { url = it })
                EditorChoice(T("route.ruleSetFormat"), T(formats.first { it.first == format }.second), formats.map { T(it.second) }) { format = formats[it].first }
                val actionOptions = listOf("route.targetProxy", "route.targetDirect", "route.targetBlock")
                EditorChoice(T("route.ruleSetAction"), T(actionOptions[action.ordinal]), actionOptions.map { T(it) }) { action = RouteAction.entries[it] }
                EditorInput(T("route.ruleSetInterval"), interval, { interval = it })
            }
        },
        confirmButton = { TextButton({
            val parsedInterval = interval.toIntOrNull()
            if (parsedInterval == null) error = invalidMessage else runCatching {
                RemoteRoutingRuleSet(name = name.trim(), url = url.trim(), format = format, action = action, updateIntervalHours = parsedInterval)
            }.onSuccess(save).onFailure { error = it.message ?: invalidMessage }
        }) { Text(T("common.save")) } },
        dismissButton = { TextButton(onDismiss) { Text(T("common.cancel")) } },
    )
}

