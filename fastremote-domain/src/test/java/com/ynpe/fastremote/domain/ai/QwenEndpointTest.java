// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class QwenEndpointTest {
    private static final String HOST = "ws-test.cn-beijing.maas.aliyuncs.com";
    private static final String BASE = "https://" + HOST + "/compatible-mode/v1";

    @Test
    void acceptsBareHostAndHttpsHost() {
        assertEquals(BASE, QwenEndpoint.parse(HOST).toString());
        assertEquals(BASE, QwenEndpoint.parse("  https://" + HOST + "/  ").toString());
    }

    @Test
    void acceptsOpenAiBaseAndFullCompletionAddress() {
        assertEquals(BASE, QwenEndpoint.parse(BASE + "/").toString());
        assertEquals(BASE, QwenEndpoint.parse(BASE + "/chat/completions").toString());
    }

    @Test
    void translatesDashScopeNativeBaseToOpenAiCompatibility() {
        assertEquals(BASE, QwenEndpoint.parse("https://" + HOST + "/api/v1/").toString());
    }

    @Test
    void preservesCustomGatewayPathsPortsAndRegionalHosts() {
        assertEquals("https://gateway.example:8443/proxy/v1",
                QwenEndpoint.parse("https://gateway.example:8443/proxy/v1/").toString());
        assertEquals("https://ws-test.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1",
                QwenEndpoint.parse("ws-test.ap-southeast-1.maas.aliyuncs.com").toString());
    }

    @Test
    void rejectsCredentialsQueriesFragmentsAndInvalidSchemes() {
        for (String input : new String[] {"", "https://user:password@host.test", "https://host.test?key=test",
                "https://host.test/#fragment", "file:///tmp/model", "not a host"}) {
            assertThrows(IllegalArgumentException.class, () -> QwenEndpoint.parse(input));
        }
    }

    @Test
    void keepsExistingInsecureRemoteHttpOptInRequirement() {
        var uri = QwenEndpoint.parse("http://gateway.example");
        assertThrows(IllegalArgumentException.class, () -> new ModelProviderConfig("qwen", "Qwen",
                ModelProviderType.QWEN_DASHSCOPE, uri, "", Optional.empty(), false));
    }
}
