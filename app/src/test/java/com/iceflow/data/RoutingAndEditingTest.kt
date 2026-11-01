package com.iceflow.data

import com.iceflow.data.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RoutingAndEditingTest {
    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun defaultsAndOrderedRulesReachRuntime() {
        val defaults = RoutingRules.defaults()
        assertTrue(defaults.filter { it.id in listOf("builtin:cn-domain", "builtin:cn-ip", "builtin:private") }.all { it.enabled })
        val block = RoutingRule(name = "Block", action = RouteAction.BLOCK, domains = listOf("full:ads.example"))
        val proxy = RoutingRule(name = "Proxy", action = RouteAction.PROXY, port = "443,8000-9000", network = "tcp", source = listOf("192.168.1.0/24"), sourcePort = "1000-65535", protocols = listOf("tls"))
        listOf(block, proxy).forEach { it.validate() }
        val config = Json.parseToJsonElement(XrayConfigBuilder.build(ServerProfile(name = "test", protocol = Protocol.VLESS, address = "proxy.example", port = 443, uuid = uuid), AppSettings(routingRules = listOf(block, proxy) + defaults))).jsonObject
        val rules = config.getValue("routing").jsonObject.getValue("rules").jsonArray
        assertEquals(5, rules.size)
        assertEquals("block", rules[0].jsonObject.getValue("outboundTag").jsonPrimitive.content)
        assertEquals(proxy.toXray(), rules[1])
        assertEquals("geosite:cn", rules[2].jsonObject.getValue("domain").jsonArray[0].jsonPrimitive.content)
        assertTrue(rules.last().jsonObject.getValue("ip").jsonArray.any { it.jsonPrimitive.content == "192.168.0.0/16" })
    }

    @Test fun jsonExportImportPreservesConditionsAndCreatesFreshIds() {
        val rules = RoutingRules.defaults()
        assertEquals(rules, RoutingRules.decode(RoutingRules.encode(rules), preserveIds = true))
        val imported = RoutingRules.decode(RoutingRules.encode(rules))
        assertEquals(rules.map { it.copy(id = "") }, imported.map { it.copy(id = "") })
        assertTrue(imported.none { node -> rules.any { it.id == node.id } })
        val xray = RoutingRules.decode("""{"routing":{"rules":[{"type":"field","outboundTag":"block","domain":["domain:example.org"],"network":"tcp","port":"80,443"}]}}""").single()
        assertEquals(RouteAction.BLOCK, xray.action)
        assertNull(xray.outboundTargetId)
        assertEquals(listOf("domain:example.org"), xray.domains)
        rejects { RoutingRules.decode("""[{"outboundTag":"direct","domain":["example.org"],"inboundTag":["secret"]}]""") }
        rejects { RoutingRules.decode("""[{"outboundTag":"unknown","port":"443"}]""") }
    }

    @Test fun referencedNodeGetsOnlyItsOwnXrayOutboundAndMissingTargetFallsBackToProxy() {
        val current = ServerProfile(id = "current", name = "Current", protocol = Protocol.VLESS, address = "current.example", port = 443, uuid = uuid)
        val target = ServerProfile(id = "target", name = "Target", protocol = Protocol.VLESS, address = "target.example", port = 443, uuid = uuid)
        val rule = RoutingRule(name = "selected", action = RouteAction.DIRECT, outboundTargetId = "node:target", domains = listOf("full:example.org"))
        val root = Json.parseToJsonElement(XrayConfigBuilder.build(current, AppSettings(routingRules = listOf(rule)), store = ServerStore(servers = listOf(current, target)))).jsonObject
        val targetTag = RoutingOutboundTargets.resolve("node:target", ServerStore(servers = listOf(current, target)))!!.tag + "-0-0"
        val outbounds = root.getValue("outbounds").jsonArray.map { it.jsonObject }
        assertTrue(outbounds.any { it["tag"]?.jsonPrimitive?.content == targetTag })
        assertEquals(targetTag, root.getValue("routing").jsonObject.getValue("rules").jsonArray.first().jsonObject.getValue("outboundTag").jsonPrimitive.content)
        val missing = rule.copy(outboundTargetId = "node:deleted")
        val fallback = Json.parseToJsonElement(XrayConfigBuilder.build(current, AppSettings(routingRules = listOf(missing)), store = ServerStore(servers = listOf(current)))).jsonObject
        assertEquals("proxy", fallback.getValue("routing").jsonObject.getValue("rules").jsonArray.first().jsonObject.getValue("outboundTag").jsonPrimitive.content)
    }

    @Test fun referencedGroupBuildsXrayBalancerAndUsesBalancerTag() {
        val a = ServerProfile(id = "a", name = "A", protocol = Protocol.VLESS, address = "a.example", port = 443, uuid = uuid, groupId = "g")
        val b = a.copy(id = "b", name = "B", address = "b.example")
        val store = ServerStore(servers = listOf(a, b), localGroups = listOf(LocalGroup(id = "g", name = "Group")))
        val target = RoutingOutboundTargets.resolve("group:g", store)!!
        val rule = RoutingRule(name = "group route", outboundTargetId = "group:g", domains = listOf("full:example.org"))
        val root = Json.parseToJsonElement(XrayConfigBuilder.build(a.copy(groupId = null), AppSettings(routingRules = listOf(rule)), store = store)).jsonObject
        val balancers = root.getValue("routing").jsonObject.getValue("balancers").jsonArray
        assertTrue(balancers.any { it.jsonObject["tag"]?.jsonPrimitive?.content == target.tag })
        val first = root.getValue("routing").jsonObject.getValue("rules").jsonArray.first().jsonObject
        assertEquals(target.tag, first.getValue("balancerTag").jsonPrimitive.content)
    }

    @Test fun invalidRulesAndMeasurementUrlsAreRejected() {
        listOf("256.0.0.1", "192.168.0.1/33", "::::", "::1/129").forEach { ip -> rejects { RoutingRule(name = "bad", ips = listOf(ip)).validate() } }
        listOf("0", "70000", "443-80", "abc").forEach { port -> rejects { RoutingRule(name = "bad", port = port).validate() } }
        rejects { RoutingRule(name = "empty").validate() }
        RoutingRule(name = "v6", ips = listOf("2001:db8::/32", "::1")).validate()
        assertEquals("https://example.org/check?q=1", RoutingRules.validateTestUrl(" https://example.org/check?q=1 "))
        listOf("ftp://example.org", "https://a:b@example.org", "http://example.org:70000/", "https://example.org/#frag", "garbage").forEach { rejects { RoutingRules.validateTestUrl(it) } }
    }

    @Test fun nativeAndExtendedNodeFormsValidateAndPreserveParameters() {
        rejects { NodeEditing.validate(NodeEditing.blank()) }
        val vless = NodeEditing.blank().copy(name = " VLESS ", address = "example.org", uuid = uuid, network = "grpc", serviceName = "service", tls = "reality", sni = "example.org", publicKey = "key")
        assertEquals("VLESS", NodeEditing.validate(vless).name)
        rejects { NodeEditing.validate(vless.copy(uuid = "bad")) }
        rejects { NodeEditing.validate(vless.copy(publicKey = "")) }
        val ssr = NodeEditing.blank(Protocol.SHADOWSOCKSR).copy(name = "SSR", address = "example.org", password = "pw", protocolOptions = mapOf("protocol" to "auth_aes128_md5", "protocol-param" to "user:secret", "obfs" to "tls1.2_ticket_auth", "obfs-param" to "cdn.example.org"))
        NodeEditing.validate(ssr)
        val options = ExtendedConfigBuilder.build(ssr)
        assertEquals("user:secret", options.getValue("protocol-param").jsonPrimitive.content)
        assertEquals("cdn.example.org", options.getValue("obfs-param").jsonPrimitive.content)
        assertEquals(mapOf("mtu" to "1400"), NodeEditing.options("""{"mtu":"1400"}"""))
        rejects { NodeEditing.options("""{"mtu":1400}""") }
    }

    @Test fun clashFormChangesKeepNestedSettingsAndOwnership() {
        val original = ShareLinkParser.parseMany("""{"proxies":[{"name":"Clash","type":"vless","server":"old.example","port":443,"uuid":"$uuid","network":"ws","tls":true,"ws-opts":{"path":"/old","headers":{"Host":"old.example","X-Custom":"retain"}},"smux":{"enabled":true,"max-connections":4}}]}""").single().copy(groupId = "group", subscriptionId = "subscription", latencyMs = 40)
        val edited = NodeEditing.syncClash(original, original.copy(name = "Edited", address = "new.example", port = 8443, path = "/new", host = "cdn.example"))
        assertEquals(original.id, edited.id); assertEquals(original.groupId, edited.groupId); assertEquals(original.subscriptionId, edited.subscriptionId); assertNull(edited.latencyMs)
        val config = edited.clashConfig!!
        assertEquals(4, config.getValue("smux").jsonObject.getValue("max-connections").jsonPrimitive.int)
        assertEquals("retain", config.getValue("ws-opts").jsonObject.getValue("headers").jsonObject.getValue("X-Custom").jsonPrimitive.content)
        assertEquals("/new", config.getValue("ws-opts").jsonObject.getValue("path").jsonPrimitive.content)
        assertEquals(8443, config.getValue("port").jsonPrimitive.int)
        val raw = NodeEditing.clash(config.toString().replace("\"max-connections\":4", "\"max-connections\":6"), edited)
        val again = NodeEditing.syncClash(raw, raw.copy(name = "After raw"))
        assertEquals(6, again.clashConfig!!.getValue("smux").jsonObject.getValue("max-connections").jsonPrimitive.int)
    }
}
