package com.iceflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iceflow.data.model.NodeGroup
import com.iceflow.data.model.LOCAL_GROUP_KEY
import com.iceflow.ui.theme.IceFlowTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupNavigationTest {
    @get:Rule val rule = createComposeRule()
    private val groups = listOf(NodeGroup(LOCAL_GROUP_KEY, null, "Local"), NodeGroup("a", "a", "Group A"), NodeGroup("b", "b", "Group B"))

    @Test fun nodePagesSwipeBothWaysAndTabsKeepSelectedGroupAfterReorder() {
        val selected = mutableStateOf<String?>(null)
        val ordered = mutableStateOf(groups)
        rule.setContent { IceFlowTheme {
            GroupPager(ordered.value, selected.value, { selected.value = it }, Modifier.fillMaxSize()) { group -> Text("Nodes: ${group.name}") }
        } }
        rule.onNodeWithText("Nodes: Local").assertIsDisplayed()
        rule.onNodeWithTag("group-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("a", selected.value) }
        rule.onNodeWithTag("group-pager").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.runOnIdle { assertNull(selected.value) }
        rule.onNodeWithTag("group-tab:b").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Nodes: Group B").assertIsDisplayed()
        rule.runOnIdle { assertEquals("b", selected.value); ordered.value = groups.reversed() }
        rule.onNodeWithText("Nodes: Group B").assertIsDisplayed()
        rule.runOnIdle { assertEquals("b", selected.value); selected.value = "a" }
        rule.onNodeWithText("Nodes: Group A").assertIsDisplayed()
    }

    @Test fun groupHandleLongPressDragsAcrossGroupsAndCommitsOrder() {
        val ordered = mutableStateOf(groups)
        var commits = 0
        rule.setContent { IceFlowTheme {
            ReorderableGroupList(ordered.value, { keys ->
                commits++; ordered.value = keys.map { key -> groups.first { it.key == key } }
            }, Modifier.fillMaxSize()) { group, handle ->
                Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(handle) { Text("=") }; Text(group.name)
                }
            }
        } }
        val first = rule.onNodeWithTag("group-drag:$LOCAL_GROUP_KEY")
        val start = first.fetchSemanticsNode().boundsInRoot.center
        val end = rule.onNodeWithTag("group-drag:b").fetchSemanticsNode().boundsInRoot.center
        first.performTouchInput {
            down(center); advanceEventTime(650)
            for (step in 1..20) { moveTo(center + Offset(0f, (end.y - start.y + 20f) * step / 20f), delayMillis = 32) }
            up()
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf("a", "b", LOCAL_GROUP_KEY), ordered.value.map { it.key }); assertEquals(1, commits) }
        assertTrue(rule.onNodeWithText("Local").fetchSemanticsNode().boundsInRoot.top > rule.onNodeWithText("Group B").fetchSemanticsNode().boundsInRoot.top)
        val last = rule.onNodeWithTag("group-drag:$LOCAL_GROUP_KEY")
        val lastCenter = last.fetchSemanticsNode().boundsInRoot.center
        val topCenter = rule.onNodeWithTag("group-drag:a").fetchSemanticsNode().boundsInRoot.center
        last.performTouchInput {
            down(center); advanceEventTime(650)
            for (step in 1..20) { moveTo(center + Offset(0f, (topCenter.y - lastCenter.y - 20f) * step / 20f), delayMillis = 32) }
            up()
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(groups.map { it.key }, ordered.value.map { it.key }); assertEquals(2, commits) }
        rule.onNodeWithTag("group-drag:$LOCAL_GROUP_KEY").performTouchInput {
            down(center); advanceEventTime(650)
            moveTo(center + Offset(0f, end.y - start.y + 20f), delayMillis = 300)
            cancel()
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(groups.map { it.key }, ordered.value.map { it.key }); assertEquals(2, commits) }
    }
}
