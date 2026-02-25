package bisq.api.access.permissions;

import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestPermissionMappingTest {
    private final RestPermissionMapping mapping = new RestPermissionMapping();

    @Test
    void mapsSupportExportToTradeChatChannelsPermission() {
        Permission required = mapping.getRequiredPermission("/api/v1/support/export", "GET");

        assertEquals(Permission.TRADE_CHAT_CHANNELS, required);
    }

    @Test
    void mapsSupportChannelMessagesToTradeChatChannelsPermission() {
        Permission required = mapping.getRequiredPermission(
                "/api/v1/support/channels/support.support/messages",
                "POST");

        assertEquals(Permission.TRADE_CHAT_CHANNELS, required);
    }

    @Test
    void rejectsUnknownRestPath() {
        assertThrows(
                ForbiddenException.class,
                () -> mapping.getRequiredPermission("/api/v1/not-a-real-endpoint", "GET"));
    }

    @Test
    void doesNotStripEmbeddedApiPrefixOutsideRootPrefix() {
        assertThrows(
                ForbiddenException.class,
                () -> mapping.getRequiredPermission("/x/api/v1/support/export", "GET"));
    }
}
