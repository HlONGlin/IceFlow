package com.iceflow.ui

import androidx.activity.compose.BackHandler
import android.graphics.Typeface
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** One selectable text block allows copying arbitrary ranges across log lines. */
@Composable
fun RuntimeLogPage(readLogs: suspend () -> String, onBack: () -> Unit, logRetentionDays: Int = 3, modifier: Modifier = Modifier) {
    var logs by remember { mutableStateOf<String?>(null) }
    var live by rememberSaveable { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectionView by remember { mutableStateOf<TextView?>(null) }
    var scrollView by remember { mutableStateOf<ScrollView?>(null) }
    val logTextColor = MaterialTheme.colorScheme.onBackground.toArgb()

    BackHandler(onBack = onBack)

    LaunchedEffect(live, refresh) {
        do {
            logs = readLogs().ifBlank { "暂无日志" }
            // 自动滚动到底部
            if (live) {
                scrollView?.post { scrollView?.fullScroll(ScrollView.FOCUS_DOWN) }
                delay(2000)
            }
        } while (live)
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.primary) }
            Text("运行日志", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh++ }) { Icon(Icons.Filled.Refresh, "刷新日志", tint = MaterialTheme.colorScheme.primary) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (logRetentionDays) {
                    0 -> "永久保留"
                    else -> "保留 $logRetentionDays 天"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (selectionView != null) {
                TextButton(onClick = { selectionView?.onTextContextMenuItem(android.R.id.copy) }) {
                    Text("复制选中", color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = { live = !live }) { Text(if (live) "暂停刷新" else "实时刷新", color = MaterialTheme.colorScheme.primary) }
        }
        Text(
            "长按文字，拖动选区后复制",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                ScrollView(context).apply {
                    scrollView = this
                    addView(TextView(context).apply {
                        typeface = Typeface.MONOSPACE
                        textSize = 12f
                        setTextColor(logTextColor)
                        setTextIsSelectable(true)
                        val density = resources.displayMetrics.density
                        val padding = (16 * density).toInt()
                        setPadding(padding, padding, padding, padding)
                        setLineSpacing(3 * density, 1f)
                        val selectableView = this
                        customSelectionActionModeCallback = object : ActionMode.Callback {
                            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                                live = false
                                selectionView = selectableView
                                return true
                            }
                            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
                            override fun onDestroyActionMode(mode: ActionMode) {
                                if (selectionView === selectableView) selectionView = null
                            }
                        }
                    }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            },
            update = { scroll ->
                val textView = scroll.getChildAt(0) as TextView
                val text = logs ?: "读取日志中…"
                // Avoid replacing an active selection when a final refresh completes.
                if (textView.text.toString() != text && textView.selectionStart == textView.selectionEnd) {
                    textView.text = text
                    // 自动滚动到底部
                    if (live) {
                        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            },
        )
    }
}
