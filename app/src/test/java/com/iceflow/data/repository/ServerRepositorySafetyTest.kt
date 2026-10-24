package com.iceflow.data.repository

import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerRepositorySafetyTest {
    @get:Rule val temp = TemporaryFolder()
    private fun node(name: String) = ServerProfile(name = name, protocol = Protocol.SOCKS, address = "localhost", port = 1080)

    @Test fun damagedPrimaryRecoversPreviousCommittedDataAndCanSaveAgain() {
        val file = File(temp.root, "servers.json")
        val first = node("first")
        val repository = ServerRepository(file)
        repository.addServers(listOf(first))
        repository.addServers(listOf(node("second")))
        file.writeText("{broken")
        val recovered = ServerRepository(file)
        assertEquals(listOf(first), recovered.store.value.servers)
        assertNotNull(recovered.recoveryMessage)
        recovered.addServers(listOf(node("third")))
        assertEquals(listOf("first", "third"), ServerRepository(file).store.value.servers.map { it.name })
    }

    @Test fun unreadableFilesAreProtectedUntilAnExplicitRestore() {
        val file = File(temp.root, "servers.json").apply { writeText("broken primary") }
        val backup = File(file.path + ".bak").apply { writeText("broken backup") }
        val repository = ServerRepository(file)
        assertThrows(IllegalStateException::class.java) { repository.addServers(listOf(node("lost"))) }
        assertEquals("broken primary", file.readText())
        assertEquals("broken backup", backup.readText())
        repository.replace(ServerStore())
        assertTrue(ServerRepository(file).store.value.servers.isEmpty())
        assertEquals(2, temp.root.listFiles()!!.count { ".corrupt-" in it.name })
    }

    @Test fun failedDiskWriteDoesNotPublishUnsavedState() {
        val blockedParent = temp.newFile("not-a-directory")
        val repository = ServerRepository(File(blockedParent, "servers.json"))
        assertThrows(java.io.IOException::class.java) { repository.addServers(listOf(node("unsaved"))) }
        assertTrue(repository.store.value.servers.isEmpty())
    }

    @Test fun trafficUpdatesRetainConcurrentNodeEdits() {
        val repository = ServerRepository(File(temp.root, "servers.json"))
        val node = node("before")
        repository.addServers(listOf(node))
        repository.updateServer(node.copy(name = "after"))
        repository.addTraffic(node.id, 20, 40)
        val stored = ServerRepository(File(temp.root, "servers.json")).store.value.servers.single()
        assertEquals("after", stored.name)
        assertEquals(20L, stored.uploadBytes)
        assertEquals(40L, stored.downloadBytes)
    }
}
