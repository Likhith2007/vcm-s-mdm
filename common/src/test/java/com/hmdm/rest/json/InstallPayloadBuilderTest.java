package com.hmdm.rest.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class InstallPayloadBuilderTest {
    private final ObjectMapper M = new ObjectMapper();

    @Test public void bundleEmitsPartsNoUrl() throws Exception {
        String json = InstallPayloadBuilder.build("com.acme.app", 42, null,
                "[{\"url\":\"http://x/base.apk\",\"sha256\":\"aa\"},{\"url\":\"http://x/split.apk\",\"sha256\":\"bb\"}]");
        JsonNode n = M.readTree(json);
        assertEquals("com.acme.app", n.get("packageName").asText());
        assertEquals(42, n.get("versionCode").asInt());
        assertFalse(n.has("url"));
        assertEquals(2, n.get("parts").size());
        assertEquals("http://x/base.apk", n.get("parts").get(0).get("url").asText());
    }

    @Test public void singleAppEmitsUrlNoParts() throws Exception {
        JsonNode n = M.readTree(InstallPayloadBuilder.build("com.acme.app", 42, "http://x/app.apk", null));
        assertEquals("http://x/app.apk", n.get("url").asText());
        assertFalse(n.has("parts"));
    }

    @Test public void zeroVersionCodeOmitted() throws Exception {
        JsonNode n = M.readTree(InstallPayloadBuilder.build("com.acme.app", 0, "http://x/app.apk", null));
        assertFalse(n.has("versionCode"));
    }
}
