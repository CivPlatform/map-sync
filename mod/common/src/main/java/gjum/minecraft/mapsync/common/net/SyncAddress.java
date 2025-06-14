package gjum.minecraft.mapsync.common.net;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record SyncAddress(
    @NotNull URI address
) {
    public SyncAddress(
        final @NotNull URI address
    ) {
        final var builder = new URIBuilder();

        final String scheme = address.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Must specify a scheme (ws/wss)!");
        }
        builder.setScheme(switch (scheme) {
            case "ws", "wss", "http", "https" -> scheme;
            default -> throw new IllegalArgumentException("Only ws/wss is permitted!");
        });
        builder.setHost(address.getHost());
        builder.setPort(address.getPort());

        try {
            this.address = builder.build();
        }
        catch (final URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public @NotNull String toString() {
        return address().toString();
    }

    public static @Nullable SyncAddress of(
        final URI syncAddress
    ) {
        if (syncAddress == null) {
            return null;
        }
        try {
            return new SyncAddress(syncAddress);
        }
        catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public static @Nullable SyncAddress of(
        String syncAddress
    ) {
        if (syncAddress == null) {
            return null;
        }
        syncAddress = syncAddress.trim();
        try {
            return of(new URI(syncAddress));
        }
        catch (final URISyntaxException e) {
            return null;
        }
    }
}
