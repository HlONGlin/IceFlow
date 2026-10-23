package com.iceflow.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import com.iceflow.data.model.NodeGroup
import com.iceflow.data.model.ServerProfile
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
        val colors = MaterialTheme.colorScheme
        val nodeSurface = colors.surface
        Column(modifier.background(nodeSurface)) {
             ScrollableTabRow(
                 selectedTabIndex = pager.currentPage, edgePadding = 12.dp,
                  modifier = Modifier.height(42.dp),
                  containerColor = colors.surface, contentColor = colors.primary,
                  indicator = { positions ->
                      if (positions.isNotEmpty()) {
                          val position = positions[pager.currentPage.coerceIn(0, positions.lastIndex)]
                           Canvas(Modifier.fillMaxSize()) {
                               val y = size.height - 1.dp.toPx()
                               drawLine(colors.primary, Offset(position.left.toPx() + 12.dp.toPx(), y), Offset(position.right.toPx() - 12.dp.toPx(), y), 2.dp.toPx())
                           }
                      }
                  },
                 divider = { HorizontalDivider(color = Color.Transparent) },
            ) {
                groups.forEachIndexed { index, group ->
                    val selected = pager.currentPage == index
                    val textColor by animateColorAsState(if (selected) colors.primary else colors.onSurfaceVariant, tween(380), label = "groupText")
                    Box(Modifier.height(38.dp)
                        .selectable(selected = selected, role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { scope.launch { pager.animateScrollToPage(index, animationSpec = tween(480, easing = FastOutSlowInEasing)) } })
                        .padding(horizontal = 16.dp).testTag("group-tab:${group.key}"), contentAlignment = Alignment.Center) {
                        Text(group.name, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
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

<<<<<<< Updated upstream
/** 节点单独排序：拖动 handle 即可上下移动，松手后提交当前顺序。 */
@Composable internal fun ReorderableNodeList(
    nodes: List<ServerProfile>, onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier, contentPadding: PaddingValues, content: @Composable (ServerProfile, Modifier) -> Unit,
=======
/**
 * 通用的拖动排序列表，与 [ReorderableGroupList] 共享同一套长按拖动 + 自动滚动机制，
 * 但按 [key] 取出的唯一标识进行排序，用于节点"调序"等需要手动拖动排序的场景。
 */
@Composable fun <T> ReorderableItemList(
    items: List<T>, key: (T) -> String, onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier, content: @Composable (T, Modifier) -> Unit,
>>>>>>> Stashed changes
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
<<<<<<< Updated upstream
    var order by remember { mutableStateOf(nodes.map { it.id }) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    val commit by rememberUpdatedState(onReorder)
    val byId = nodes.associateBy { it.id }
    LaunchedEffect(nodes.map { it.id }) {
        order = if (dragging == null) nodes.map { it.id } else (order + nodes.map { it.id }).distinct().filter { it in byId }
=======
    var order by remember { mutableStateOf(items.map(key)) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(items)
    val commit by rememberUpdatedState(onReorder)
    val byKey = items.associateBy(key)
    LaunchedEffect(items.map(key)) {
        if (dragging != null && dragging !in byKey) dragging = null
        if (dragging == null) order = items.map(key)
        else order = (order + items.map(key)).distinct().filter { it in byKey }
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
        if (save && dragging != null) commit(order) else if (!save) order = nodes.map { it.id }
=======
        if (save && dragging != null) commit(order)
        else if (!save) order = currentItems.map(key)
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
    LazyColumn(state = listState, modifier = modifier, contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        items(order.mapNotNull(byId::get), key = { it.id }) { server ->
            val active = dragging == server.id
            Box(Modifier.animateItem(placementSpec = if (active) null else androidx.compose.animation.core.spring(stiffness = 500f)).zIndex(if (active) 1f else 0f).graphicsLayer {
                if (active) {
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == server.id }
                    translationY = if (item == null) 0f else center - item.offset - item.size / 2f
                    scaleX = 1.025f; scaleY = 1.025f
                }
            }) {
                val handle = Modifier.size(48.dp).testTag("node-drag:${server.id}")
                    .semantics {
                        customActions = listOf("上移" to -1, "下移" to 1).map { (label, offset) ->
                            CustomAccessibilityAction(label) {
                                val from = order.indexOf(server.id); val to = from + offset
                                if (from >= 0 && to in order.indices) {
                                    order = order.toMutableList().apply { add(to, removeAt(from)) }; commit(order); true
                                } else false
                            }
                        }
                    }
                    .pointerInput(server.id) {
                        detectDragGestures(
                            onDragStart = {
                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == server.id }
                                if (item != null) { dragging = server.id; center = item.offset + item.size / 2f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
=======
    LazyColumn(state = listState, modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(order.mapNotNull(byKey::get), key = key) { entry ->
            val itemKey = key(entry)
            val active = dragging == itemKey
            Box(Modifier.animateItem(placementSpec = if (active) null else androidx.compose.animation.core.spring(stiffness = 500f)).zIndex(if (active) 1f else 0f).graphicsLayer {
                if (active) {
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
                    translationY = if (item == null) 0f else center - item.offset - item.size / 2f
                    scaleX = 1.02f; scaleY = 1.02f
                }
            }) {
                val handle = Modifier.size(40.dp)
                    .pointerInput(itemKey) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
                                if (item != null) { dragging = itemKey; center = item.offset + item.size / 2f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
>>>>>>> Stashed changes
                            },
                            onDrag = { change, amount -> change.consume(); dragMove(amount.y) },
                            onDragEnd = { dragFinish(true) }, onDragCancel = { dragFinish(false) },
                        )
                    }
<<<<<<< Updated upstream
                content(server, handle)
=======
                content(entry, handle)
>>>>>>> Stashed changes
            }
        }
    }
}
