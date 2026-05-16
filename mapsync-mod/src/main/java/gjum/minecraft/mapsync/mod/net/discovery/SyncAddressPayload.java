package gjum.minecraft.mapsync.mod.net.discovery;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/// Clientbound Fabric custom payload that announces the MapSync websocket
/// endpoint to a joining client, so the client can auto-connect without GUI
/// interaction. Sent once per player join.
///
/// `host` may be empty — in that case the client substitutes the MC server's
/// own host. This keeps single-host bundled deployments configuration-free:
/// the operator only sets the MapSync port, and clients connect to
/// `ws://<mc-server-host>:<port>`. Operators running MapSync behind a proxy
/// can set `advertisedHost` in the server config to override.
public record SyncAddressPayload(
	@NotNull String host,
	int port
) implements CustomPacketPayload {
	public static final @NotNull Type<SyncAddressPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("mapsync", "sync_address"));

	public static final @NotNull StreamCodec<ByteBuf, SyncAddressPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SyncAddressPayload::host,
			ByteBufCodecs.VAR_INT, SyncAddressPayload::port,
			SyncAddressPayload::new
		);

	@Override
	public @NotNull Type<SyncAddressPayload> type() {
		return TYPE;
	}
}
