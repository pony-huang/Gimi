package dev.e2b.sdk;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import static org.junit.Assert.*;

public class JavaInteropTest {
    @Test public void javaCanCreateAndUseSandboxWithoutKotlinTypes() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            E2BConfig config = E2BConfig.builder()
                .apiKey("test-key")
                .apiUrl(server.url("/").toString())
                .sandboxUrl(server.url("/").toString())
                .build();
            server.enqueue(new MockResponse().setBody("{\"sandboxID\":\"sb\",\"envdVersion\":\"0.5.7\",\"envdAccessToken\":\"token\"}"));
            server.enqueue(new MockResponse().setBody("hello"));
            server.enqueue(new MockResponse().setResponseCode(204));
            try (Sandbox sandbox = Sandbox.create(config, new CreateOptions("base"))) {
                assertEquals("sb", sandbox.getSandboxId());
                assertEquals("hello", sandbox.getFiles().read("/tmp/message"));
                assertEquals("8080-sb.e2b.app", sandbox.getHost(8080));
            }
            assertEquals(3, server.getRequestCount());
            assertNotNull(new CommandOptions());
            assertNotNull(new ListOptions());
        }
    }
}
