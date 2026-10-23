package com.iceflow.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.iceflow.data.model.NodeGroup
import com.iceflow.ui.theme.IceBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GroupPager(
    groups: List<NodeGroup>, selectedId: String?, onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier, content: @Composable (NodeGroup) -> Unit,
) {
    // Reordering retains selection by ID rather than accidentally selecting an old index.
    key(groups.map { it.key }) {
        val pager = rememberPagerState(initialPage = groups.indexOfFirst { it.id == selectedId }.coerceAtLeast(0), pageCount = { groups.size })
        val scope = rememberCoroutineScope()
        val select by rememberUpdatedState(onSelect)
        LaunchedEffect(selectedId) {
            val index = groups.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
            if (index != pager.currentPage) pager.scrollToPage(index)
        }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.distinctUntilChanged().drop(1).collect { select(groups[it].id) }
        }
        Column(modifier) {
            ScrollableTabRow(
                selectedTabIndex = pager.currentPage, edgePadding = 16.dp,
                containerColor = Color.White, contentColor = IceBlue,
                divider = { HorizontalDivider(color = Color(0xFFEEF2F6)) },
            ) {
                groups.forEachIndexed { index, group ->
                    Tab(selected = pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        selectedContentColor = IceBlue, unselectedContentColor = Color(0xFF8E8E93),
                        text = { Text(group.name, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
                        modifier = Modifier.testTag("group-tab:${group.key}"))
                }
            }
            HorizontalPager(pager, key = { groups[it].key }, modifier = Modifier.weight(1f).fillMaxWidth().testTag("group-pager")) { index -> content(groups[index]) }
        }
    }
}

@Composable internal fun ReorderableGroupList(
    groups: List<NodeGroup>, onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier, content: @Composable (NodeGroup, Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    var order by remember { mutableStateOf(groups.map { it.key }) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    val currentGroups by rememberUpdatedState(groups)
    val commit by rememberUpdatedState(onReorder)
    val byKey = groups.associateBy { it.key }
    LaunchedEffect(groups.map { it.key }) {
        if (dragging != null && dragging !in byKey) dragging = null
        if (dragging == null) order = groups.map { it.key }
        else order = (order + groups.map { it.key }).distinct().filter { it in byKey }
    }
    fun move(direction: Float) {
        val id = dragging ?: return
        val from = order.indexOf(id)
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != id && center >= item.offset && center <= item.offset + item.size &&
                if (direction > 0) center >= item.offset + item.size / 2f else center <= item.offset + item.size / 2f
        } ?: return
        val to = order.indexOf(target.key)
        if (from < 0 || to < 0 || (direction > 0 && to <= from) || (direction < 0 && to >= from)) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun finish(save: Boolean) {
        if (save && dragging != null) commit(order)
        else if (!save) order = currentGroups.map { it.key }
        dragging = null
    }
    val dragMove by rememberUpdatedState<(Float) -> Unit>({ delta -> center += delta; move(delta) })
    val dragFinish by rememberUpdatedState<(Boolean) -> Unit>({ finish(it) })
    LaunchedEffect(dragging) {
        while (dragging != null) {
            val layout = listState.layoutInfo
            val delta = when {
                center < layout.viewportStartOffset + edge -> -18f
                center > layout.viewportEndOffset - edge -> 18f
                else -> 0f
            }
            if (delta != 0f) { listState.scrollBy(delta); move(delta) }
            delay(16)
        }
    }
    LazyColumn(state = listState, modifier = modifier.testTag("group-order-list"),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(order.mapNotNull(byKey::get), key = { it.key }) { group ->
            val active = dragging == group.key
            Box(Modifier.animateItem(placementSpec = if (active) null else androidx.compose.animation.core.spring(stiffness = 500f)).zIndex(if (active) 1f else 0f).graphicsLayer {
                if (active) {
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == group.key }
                    translationY = if (item == null) 0f else center - item.offset - item.size / 2f
                    scaleX = 1.025f; scaleY = 1.025f
                }
            }) {
                val handle = Modifier.size(40.dp).testTag("group-drag:${group.key}")
                    .semantics {
                        customActions = listOf("上移" to -1, "下移" to 1).map { (label, offset) ->
                            CustomAccessibilityAction(label) {
                                val from = order.indexOf(group.key); val to = from + offset
                                if (from >= 0 && to in order.indices) {
                                    order = order.toMutableList().apply { add(to, removeAt(from)) }; commit(order); true
                                } else false
                            }
                        }
                    }
                    .pointerInput(group.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == group.key }
                                if (item != null) { dragging = group.key; center = item.offset + item.size / 2f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                            },
                            onDrag = { change, amount -> change.consume(); dragMove(amount.y) },
                            onDragEnd = { dragFinish(true) }, onDragCancel = { dragFinish(false) },
                        )
                    }
                content(group, handle)
            }
        }
    }
}
