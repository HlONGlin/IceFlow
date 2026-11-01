package com.iceflow.data

import com.iceflow.data.model.RemoteRoutingRuleSet
import com.iceflow.data.model.RemoteRoutingRuleSetParser
import com.iceflow.data.model.RemoteRuleSetFormat
import com.iceflow.data.model.RouteAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteRoutingRuleSetParserTest {
    private fun set(format: RemoteRuleSetFormat, action: RouteAction = RouteAction.PROXY) = RemoteRoutingRuleSet(
        id = "rules", name = "Test", url = "https://rules.example/list", format = format, action = action,
    )

    @Test fun parsesTextAndClashYamlFormatsWithConfiguredAction() {
        val domains = RemoteRoutingRuleSetParser.parse(set(RemoteRuleSetFormat.DOMAIN_LIST, RouteAction.BLOCK), "# comment\nexample.com\n*.sub.example.net")
        assertEquals(listOf("domain:example.com", "domain:sub.example.net"), domains.single().domains)
        assertEquals(RouteAction.BLOCK, domains.single().action)
        val ips = RemoteRoutingRuleSetParser.parse(set(RemoteRuleSetFormat.IP_LIST), "192.0.2.0/24\n2001:db8::/32")
        assertEquals(listOf("192.0.2.0/24", "2001:db8::/32"), ips.single().ips)
        val yaml = RemoteRoutingRuleSetParser.parse(set(RemoteRuleSetFormat.CLASH_DOMAIN), "payload:\n  - one.example\n  - two.example")
        assertEquals(listOf("domain:one.example", "domain:two.example"), yaml.single().domains)
        val cidr = RemoteRoutingRuleSetParser.parse(set(RemoteRuleSetFormat.CLASH_IPCIDR), "payload:\n  - 198.51.100.0/24")
        assertEquals(listOf("198.51.100.0/24"), cidr.single().ips)
    }

    @Test fun classicalUsesConfiguredBehaviorAndRejectsUnsupportedRuleKinds() {
        val parsed = RemoteRoutingRuleSetParser.parse(set(RemoteRuleSetFormat.CLASH_CLASSICAL, RouteAction.DIRECT), "payload:\n  - DOMAIN,one.example,REJECT\n  - DOMAIN-SUFFIX,two.example,PROXY\n  - IP-CIDR,192.0.2.0/24,DIRECT")
        assertEquals(listOf("full:one.example", "domain:two.example"), parsed.first().domains)
        assertEquals(listOf("192.0.2.0/24"), parsed.last().ips)
        assertTrue(parsed.all { it.action == RouteAction.DIRECT })
        assertThrows(IllegalStateException::class.java) {
            RemoteRoutingRuleSetParser.parse(set(RemoteRuleSetFormat.CLASH_CLASSICAL), "payload:\n  - MATCH,PROXY")
        }
    }

    @Test fun enforcesHttpsSizeAndEntryLimits() {
        assertThrows(IllegalArgumentException::class.java) { set(RemoteRuleSetFormat.DOMAIN_LIST).copy(url = "http://rules.example/list").validate() }
        val tooMany = (0..RemoteRoutingRuleSetParser.MAX_ENTRIES).joinToString("\n") { "host" + it + ".example" }
        val error = assertThrows(IllegalArgumentException::class.java) { RemoteRoutingRuleSetParser.parse(set(RemoteRuleSetFormat.DOMAIN_LIST), tooMany) }
        assertTrue(error.message.orEmpty().contains("50,000"))
    }
}
