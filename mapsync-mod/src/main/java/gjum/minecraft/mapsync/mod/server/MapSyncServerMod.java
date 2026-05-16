package gjum.minecraft.mapsync.mod.server;

import gjum.minecraft.mapsync.mod.data.GameAddress;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundHandshakePacket;
import java.io.ByteArrayOutputStream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;

/// Server-side (dedicated server) entrypoint. Phase 0 made the mod load on a
/// dedicated server; Phase 1 cements which classes are safe to use here. The
/// following packages are confirmed side-neutral and may be referenced from
/// any server-side code: `net.buffers`, `net.packet`, `data`, `utils`, plus
/// `net.auth` *except* {@code AuthProcess} (which still calls
/// {@code Minecraft.getInstance()} and is strictly client-only).
///
/// Phase 2 will plug the websocket server, persistence, and Mojang auth
/// directly into the Minecraft server lifecycle from here.
public final class MapSyncServerMod {
	public static final Logger logger = LogManager.getLogger(MapSyncServerMod.class);

	@ApiStatus.Internal
	public static void bootstrap() {
		runSharedProtocolSanityCheck();
		ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
			logger.info("MapSync server-side initialized (no-op stub — Phase 1)");
		});
	}

	/// Encodes a packet through the shared wire-protocol classes at startup so
	/// any future client-only import sneaking into `net/buffers`, `net/packet`,
	/// `data`, or `utils` fails loudly here rather than at the first real
	/// websocket connection in Phase 2+.
	private static void runSharedProtocolSanityCheck() {
		try {
			final var packet = new ServerboundHandshakePacket(
				"phase-1-sanity-check",
				new GameAddress("localhost:25565")
			);
			final var sink = new ByteArrayOutputStream();
			Packet.encodePacket(new BufferWriter(sink), packet);
			final byte[] encoded = sink.toByteArray();
			if (encoded.length == 0 || (encoded[0] & 0xFF) != ServerboundHandshakePacket.PACKET_ID) {
				throw new IllegalStateException(
					"Round-trip produced unexpected packet id: "
						+ (encoded.length == 0 ? "<empty>" : Integer.toString(encoded[0] & 0xFF))
				);
			}
			logger.info("MapSync shared protocol load check OK ({} bytes encoded)", encoded.length);
		}
		catch (final Exception e) {
			throw new RuntimeException(
				"MapSync shared protocol sanity check failed — server-side classloading regressed",
				e
			);
		}
	}
}
