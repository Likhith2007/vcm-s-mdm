package com.hmdm.persistence.domain;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ApplicationVersionPartsTest {
    @Test
    public void copiesPartsFromApplication() {
        Application a = new Application();
        a.setParts("[{\"url\":\"http://x/base.apk\",\"sha256\":\"aa\"}]");
        assertEquals(a.getParts(), new ApplicationVersion(a).getParts());
    }
    @Test
    public void nullPartsStaysNull() {
        assertNull(new ApplicationVersion(new Application()).getParts());
    }
}
