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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.iceflow.ui.theme.IceBlue
import kotlinx.coroutines.delay

/** One selectable text block allows copying arbitrary ranges across log lines. */
@Composable
fun RuntimeLogPage(readLogs: suspend () -> String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var logs by remember { mutableStateOf<String?>(null) }
    var live by rememberSaveable { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectionView by remember { mutableStateOf<TextView?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(live, refresh) {
        do {
            logs = readLogs().ifBlank { "暂无日志" }
            if (live) delay(2000)
        } while (live)
    }
    Column(modifier.fillMaxSize().background(Color.White).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = IceBlue) }
            Text("运行日志", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh++ }) { Icon(Icons.Filled.Refresh, "刷新日志", tint = IceBlue) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("长按文字，拖动选区后复制", style = MaterialTheme.typography.bodySmall, color = Color(0xFF737D8C), modifier = Modifier.weight(1f))
            if (selectionView != null) {
                TextButton(onClick = { selectionView?.onTextContextMenuItem(android.R.id.copy) }) {
                    Text("复制选中", color = IceBlue)
                }
            }
            TextButton(onClick = { live = !live }) { Text(if (live) "暂停刷新" else "实时刷新", color = IceBlue) }
        }
        HorizontalDivider(color = Color(0xFFEAF0F6))
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                ScrollView(context).apply {
                    addView(TextView(context).apply {
                        typeface = Typeface.MONOSPACE
                        textSize = 12f
                        setTextColor(android.graphics.Color.rgb(39, 52, 72))
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
                }
            },
        )
    }
}
