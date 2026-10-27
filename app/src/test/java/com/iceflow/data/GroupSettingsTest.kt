package com.iceflow.data

import com.iceflow.data.model.*
import com.iceflow.data.repository.ServerRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.serialization.json.*

class GroupSettingsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun groupOrderingPersistsAcrossTypesRefreshDeletionAndNewGroups() {
        val file = temp.newFile()
        val repository = ServerRepository(file)
        val a = GroupConfiguration(id = "a", name = "Local")
        val b = GroupConfiguration(id = "b", name = "Subscription", subscription = true, url = "https://example.com/b")
        repository.saveGroup(a); repository.saveGroup(b)
        repository.selectGroup(a.id)
        repository.reorderGroups(listOf(b.id, a.id, b.id, "unknown"))
        assertEquals(listOf(b.id, a.id), ServerRepository(file).store.value.orderedGroups.map { it.key })
        assertEquals(a.id, repository.store.value.selectedGroupId)
        repository.saveGroup(a.copy(subscription = true, url = "https://example.com/a"))
        repository.upsertSubscription(b.url, b.name, emptyList(), b.id)
        assertEquals(listOf(b.id, a.id), repository.store.value.groupOrder)
        repository.saveGroup(GroupConfiguration(id = "c", name = "New"))
        assertEquals(listOf(b.id, a.id, "c"), repository.store.value.groupOrder)
        repository.deleteSubscription(b.id)
        assertEquals(listOf(a.id, "c"), ServerRepository(file).store.value.groupOrder)
    }

    @Test fun legacyStoresGetCompleteDefaultOrderAndRetainItOnMutation() {
        val file = temp.newFile()
        file.writeText("""{"localGroups":[{"id":"a","name":"Local"}],"subscriptions":[{"id":"b","name":"Sub","url":"https://example.com"}],"groupOrder":["builtin:local","a","b"]}""")
        val repository = ServerRepository(file)
        assertEquals(listOf("a", "b"), repository.store.value.orderedGroups.map { it.key })
        repository.reorderGroups(listOf("b"))
        assertEquals(listOf("b", "a"), ServerRepository(file).store.value.orderedGroups.map { it.key })
        assertTrue(repository.store.value.orderedGroups.none { it.id == null })
    }

    @Test fun newStoreStartsWithoutGroupsAndImportedNodesStayUngrouped() {
        val repository = ServerRepository(temp.newFile())
        assertTrue(repository.store.value.orderedGroups.isEmpty())
        assertNull(repository.store.value.selectedGroupId)

        val node = ServerProfile(name = "Imported", protocol = Protocol.VLESS, address = "example.com", port = 443)
        repository.addServers(listOf(node))

        assertTrue(repository.store.value.orderedGroups.isEmpty())
        assertNull(repository.store.value.servers.single().groupId)
        assertNull(repository.store.value.servers.single().subscriptionId)
    }

    @Test fun settingsSurviveReloadAndTypeConversionsPreserveNodes() {
        val file = temp.newFile()
        val repository = ServerRepository(file)
        val config = GroupConfiguration(name = "Test", sort = GroupSort.NAME)
        repository.saveGroup(config)
        val node = ServerProfile(name = "Node", protocol = Protocol.VLESS, address = "example.com", port = 443, groupId = config.id)
        repository.addServers(listOf(node))
        repository.selectGroup(config.id)
        repository.saveGroup(config.copy(subscription = true, url = "https://example.com/sub", intervalHours = 12))
        val reloaded = ServerRepository(file)
        assertTrue(reloaded.store.value.localGroups.isEmpty())
        assertEquals(12, reloaded.store.value.subscriptions.single().updateIntervalHours)
        assertEquals(GroupSort.NAME, reloaded.store.value.subscriptions.single().sort)
        assertEquals(config.id, reloaded.store.value.servers.single().subscriptionId)
        assertNull(reloaded.store.value.servers.single().groupId)
        reloaded.saveGroup(config)
        assertTrue(reloaded.store.value.subscriptions.isEmpty())
        assertEquals(config.id, reloaded.store.value.servers.single().groupId)
        assertNull(reloaded.store.value.servers.single().subscriptionId)
        reloaded.deleteLocalGroup(config.id)
        assertNull(reloaded.store.value.servers.single().groupId)
        assertNull(reloaded.store.value.selectedGroupId)
    }

    @Test fun deletedSubscriptionCannotBeRestoredByAnInFlightRefresh() {
        val repository = ServerRepository(temp.newFile())
        val config = GroupConfiguration(name = "Sub", subscription = true, url = "https://example.com/sub")
        val node = ServerProfile(name = "Node", protocol = Protocol.VLESS, address = "example.com", port = 443)
        repository.saveGroup(config)
        repository.upsertSubscription(config.url, config.name, listOf(node), config.id)
        repository.selectGroup(config.id)
        repository.deleteSubscription(config.id)
        repository.upsertSubscription(config.url, config.name, listOf(node), config.id)
        assertTrue(repository.store.value.subscriptions.isEmpty())
        assertTrue(repository.store.value.servers.isEmpty())
        assertNull(repository.store.value.selectedGroupId)
        assertNull(repository.store.value.selectedId)
    }

    @Test fun sortingAndValidationCoverInvalidInputAndUntestedNodes() {
        assertNotNull(GroupConfiguration(name = "Sub", subscription = true, url = "https:").validationError())
        assertNull(GroupConfiguration(name = "Sub", subscription = true, url = "https://example.com", intervalHours = 0).validationError())
        val nodes = listOf(null, -1, 80, 10).map { ServerProfile(name = "N$it", protocol = Protocol.VLESS, address = "example.com", port = 443, latencyMs = it) }
        assertEquals(listOf(10, 80, -1, null), GroupSort.LATENCY.apply(nodes).map { it.latencyMs })
        assertEquals(nodes, GroupSort.ORIGINAL.apply(nodes))
    }

    @Test fun httpSubscriptionUrlIsRejected() {
        assertNotNull(GroupConfiguration(name = "Sub", subscription = true, url = "http://example.com/sub").validationError())
    }

    @Test fun chainSettingsPersistAcrossConversionAndGenerateOrderedHops() {
        val file = temp.newFile()
        val repository = ServerRepository(file)
        val front = ServerProfile(name = "Front", protocol = Protocol.SOCKS, address = "front.example", port = 1080)
        val landing = front.copy(id = "landing", name = "Landing", protocol = Protocol.HTTP, address = "landing.example", port = 8080)
        repository.addServers(listOf(front, landing))
        val config = GroupConfiguration(name = "Chain", frontNodeId = front.id, landingNodeId = landing.id)
        repository.saveGroup(config)
        val middle = ServerProfile(name = "Middle", protocol = Protocol.VLESS, uuid = "00000000-0000-4000-8000-000000000001", address = "middle.example", port = 443, groupId = config.id)
        repository.addServers(listOf(middle))
        val store = ServerRepository(file).store.value
        assertEquals(front.id, store.localGroups.single().frontNodeId)
        assertEquals(landing.id, store.localGroups.single().landingNodeId)
        val json = Json.parseToJsonElement(XrayConfigBuilder.build(middle, AppSettings(), store = store)).jsonObject
        val outbounds = json.getValue("outbounds").jsonArray
        assertEquals("proxy", outbounds.first().jsonObject.getValue("tag").jsonPrimitive.content)
        val native = outbounds[1].jsonObject
        assertEquals("vless", native.getValue("protocol").jsonPrimitive.content)
        assertEquals("chain-0", native.getValue("streamSettings").jsonObject.getValue("sockopt").jsonObject.getValue("dialerProxy").jsonPrimitive.content)
        val adapters = json.getValue("iceflowAdapters").jsonArray
        assertEquals(listOf("chain-0", "proxy"), adapters.map { it.jsonObject.getValue("tag").jsonPrimitive.content })
        assertEquals("chain-1", adapters.last().jsonObject.getValue("upstream").jsonPrimitive.content)
        repository.saveGroup(config.copy(subscription = true, url = "https://example.com/sub"))
        assertEquals(front.id, repository.store.value.subscriptions.single().frontNodeId)
        repository.saveGroup(config)
        assertEquals(landing.id, repository.store.value.localGroups.single().landingNodeId)
        repository.delete(front.id)
        assertTrue(runCatching { XrayConfigBuilder.build(middle, AppSettings(), store = repository.store.value) }.isFailure)
        assertTrue(runCatching { repository.saveGroup(config) }.isFailure)
        assertNotNull(config.copy(landingNodeId = front.id).validationError())
        assertTrue(runCatching { repository.saveGroup(config.copy(frontNodeId = middle.id)) }.isFailure)
    }

    @Test fun reorderServersUpdatesOrderAndSetsOriginalSort() {
        val file = temp.newFile()
        val repository = ServerRepository(file)
        val groupA = GroupConfiguration(name = "A")
        repository.saveGroup(groupA)
        val groupB = GroupConfiguration(name = "B", subscription = true, url = "https://example.com/b")
        repository.saveGroup(groupB)
        // 三个节点在 groupA 里
        val n1 = ServerProfile(name = "N1", protocol = Protocol.VLESS, address = "a.example", port = 443, groupId = groupA.id)
        val n2 = ServerProfile(name = "N2", protocol = Protocol.VLESS, address = "b.example", port = 443, groupId = groupA.id)
        val n3 = ServerProfile(name = "N3", protocol = Protocol.VLESS, address = "c.example", port = 443, groupId = groupA.id)
        // 一个节点在 groupB（不应受影响）
        val nOther = ServerProfile(name = "Other", protocol = Protocol.VLESS, address = "other.example", port = 443, subscriptionId = groupB.id)
        repository.addServers(listOf(n1, n2, n3, nOther))
        // 改变为 N3, N1, N2 的顺序
        repository.reorderServers(groupA.id, listOf(n3.id, n1.id, n2.id))
        val store = repository.store.value
        val groupANodes = store.servers.filter { it.groupId == groupA.id }
        assertEquals(listOf(n3.id, n1.id, n2.id), groupANodes.map { it.id })
        assertEquals(GroupSort.ORIGINAL, store.localGroups.first { it.id == groupA.id }.sort)
        // groupB 中的节点顺序未变
        assertEquals(nOther.id, store.servers.first { it.subscriptionId == groupB.id }.id)
        // 持久化后重新加载结果一致
        val reloaded = ServerRepository(file).store.value
        assertEquals(listOf(n3.id, n1.id, n2.id), reloaded.servers.filter { it.groupId == groupA.id }.map { it.id })
    }

    @Test fun reorderServersOnLocalGroup_setsLocalSort() {
        val repository = ServerRepository(temp.newFile())
        val n1 = ServerProfile(name = "N1", protocol = Protocol.VLESS, address = "a.example", port = 443)
        val n2 = ServerProfile(name = "N2", protocol = Protocol.VLESS, address = "b.example", port = 443)
        repository.addServers(listOf(n1, n2))
        // 先设置为 NAME 排序
        repository.setGroupSort(null, GroupSort.NAME)
        assertEquals(GroupSort.NAME, repository.store.value.localSort)
        // 手动重排后，本地分组排序应切回 ORIGINAL
        repository.reorderServers(null, listOf(n2.id, n1.id))
        assertEquals(GroupSort.ORIGINAL, repository.store.value.localSort)
        val localNodes = repository.store.value.servers.filter { it.subscriptionId == null && it.groupId == null }
        assertEquals(listOf(n2.id, n1.id), localNodes.map { it.id })
    }

    @Test fun partialReorderPreservesOtherGroupsAndDisplayedOrderAfterReload() {
        val file = temp.newFile()
        val repository = ServerRepository(file)
        val group = GroupConfiguration(name = "Group", sort = GroupSort.NAME)
        repository.saveGroup(group)
        val first = ServerProfile(id = "first", name = "A", protocol = Protocol.SOCKS, address = "a.example", port = 1080, groupId = group.id)
        val second = first.copy(id = "second", name = "B")
        val third = first.copy(id = "third", name = "C")
        val other = first.copy(id = "other", groupId = null)
        repository.addServers(listOf(first, other, second, third))

        repository.reorderServers(group.id, listOf(third.id, "deleted", third.id, other.id))

        val reloaded = ServerRepository(file).store.value
        assertEquals(listOf(third.id, other.id, first.id, second.id), reloaded.servers.map { it.id })
        assertEquals(other, reloaded.servers[1])
        val savedGroup = reloaded.localGroups.single()
        assertEquals(GroupSort.ORIGINAL, savedGroup.sort)
        assertEquals(listOf(third.id, first.id, second.id), savedGroup.sort.apply(reloaded.servers.filter { it.groupId == group.id }).map { it.id })
    }

    @Test fun subscriptionRefreshKeepsReferencedNodeIdentityAndDoesNotReplaceRemovedHop() {
        val repository = ServerRepository(temp.newFile())
        val sub = GroupConfiguration(name = "Provider", subscription = true, url = "https://example.com/sub")
        repository.saveGroup(sub)
        val original = ServerProfile(name = "Front", protocol = Protocol.SOCKS, address = "front.example", port = 1080)
        repository.upsertSubscription(sub.url, sub.name, listOf(original), sub.id)
        val group = GroupConfiguration(name = "Chain", frontNodeId = original.id)
        repository.saveGroup(group)
        repository.upsertSubscription(sub.url, sub.name, listOf(original.copy(id = "new-import", name = "Renamed")), sub.id)
        assertEquals(original.id, repository.store.value.servers.single().id)
        assertEquals(original.id, repository.store.value.localGroups.single().frontNodeId)
        val middle = original.copy(id = "middle", groupId = group.id, subscriptionId = null)
        repository.addServers(listOf(middle))
        repository.upsertSubscription(sub.url, sub.name, listOf(original.copy(id = "other", address = "other.example")), sub.id)
        assertTrue(runCatching { XrayConfigBuilder.build(middle, AppSettings(), store = repository.store.value) }.isFailure)
    }
}
