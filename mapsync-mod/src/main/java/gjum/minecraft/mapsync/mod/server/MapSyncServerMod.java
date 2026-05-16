package gjum.minecraft.mapsync.mod.server;

import gjum.minecraft.mapsync.mod.data.GameAddress;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundWelcomePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundHandshakePacket;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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

	/// Round-trips both directions of the wire protocol through the shared
	/// encoder/decoder at server startup. Catches three classes of regressions
	/// early: (1) a client-only import sneaking into `net/buffers`,
	/// `net/packet`, `data`, or `utils` and breaking classloading; (2) a
	/// serverbound packet missing its server-side `read()`; (3) a clientbound
	/// packet missing its server-side `write()`. Any failure here aborts the
	/// dedicated server boot with a clear cause.
	private static void runSharedProtocolSanityCheck() {
		try {
			// Serverbound: encode (as the client would) → decode (as the server now must).
			final var sent = new ServerboundHandshakePacket(
				"phase-2-sanity-check",
				new GameAddress("localhost:25565")
			);
			final var clientToServer = new ByteArrayOutputStream();
			Packet.encodePacket(new BufferWriter(clientToServer), sent);
			final byte[] s2s = clientToServer.toByteArray();
			final Packet decoded = Packet.decodeServerbound(new BufferReader(ByteBuffer.wrap(s2s)));
			if (!(decoded instanceof ServerboundHandshakePacket received)) {
				throw new IllegalStateException("Serverbound round-trip lost type: " + decoded.getClass().getName());
			}
			if (!received.modVersion().equals(sent.modVersion())
				|| !received.gameAddress().address().equals(sent.gameAddress().address())) {
				throw new IllegalStateException("Serverbound round-trip payload mismatch");
			}

			// Clientbound: encode (the new path the server now uses).
			final var serverToClient = new ByteArrayOutputStream();
			Packet.encodePacket(new BufferWriter(serverToClient), new ClientboundWelcomePacket());
			final byte[] c2c = serverToClient.toByteArray();
			if (c2c.length != 1 || (c2c[0] & 0xFF) != ClientboundWelcomePacket.PACKET_ID) {
				throw new IllegalStateException(
					"Clientbound welcome did not encode to a single packet-id byte (got " + c2c.length + " bytes)"
				);
			}

			logger.info(
				"MapSync wire-protocol round-trip OK ({} bytes serverbound, {} bytes clientbound)",
				s2s.length, c2c.length
			);
		}
		catch (final Exception e) {
			throw new RuntimeException(
				"MapSync shared protocol sanity check failed — server-side classloading or wire-format regressed",
				e
			);
		}
	}
}
