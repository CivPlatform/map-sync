package gjum.minecraft.mapsync.mod.net;

import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundChunkTimestampsResponsePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundIdentityRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundRegionTimestampsPacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundWelcomePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundCatchupRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundChunkTimestampsRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundDimensionChangePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundHandshakePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundIdentityResponsePacket;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

public interface Packet {
	public default void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		throw new NotImplementedException();
	}

	/// Client-side decode: turns server→client bytes into a Packet record.
	public static @NotNull Packet decodePacket(
		final @NotNull BufferReader reader
	) throws Exception {
		final int packetId = reader.readUnt8();
		return switch (packetId) {
			case ChunkTilePacket.PACKET_ID -> ChunkTilePacket.read(reader);
			case ClientboundIdentityRequestPacket.PACKET_ID -> ClientboundIdentityRequestPacket.read(reader);
			case ClientboundWelcomePacket.PACKET_ID -> ClientboundWelcomePacket.read(reader);
			case ClientboundChunkTimestampsResponsePacket.PACKET_ID -> ClientboundChunkTimestampsResponsePacket.read(reader);
			case ClientboundRegionTimestampsPacket.PACKET_ID -> ClientboundRegionTimestampsPacket.read(reader);
			default -> throw new UnexpectedPacketException((byte) packetId);
		};
	}

	/// Server-side decode: turns client→server bytes into a Packet record.
	public static @NotNull Packet decodeServerbound(
		final @NotNull BufferReader reader
	) throws Exception {
		return decodeServerboundFromId(reader.readUnt8(), reader);
	}

	/// Same as [decodeServerbound] but with the packet-id byte already peeled
	/// off the reader. The bundled server reads the id first so it can branch
	/// out ChunkTilePackets — those carry parsed BlockColumns that pull in
	/// client-only state — before falling back to the generic switch.
	public static @NotNull Packet decodeServerboundFromId(
		final int packetId,
		final @NotNull BufferReader reader
	) throws Exception {
		return switch (packetId) {
			case ServerboundHandshakePacket.PACKET_ID -> ServerboundHandshakePacket.read(reader);
			case ServerboundIdentityResponsePacket.PACKET_ID -> ServerboundIdentityResponsePacket.read(reader);
			case ServerboundDimensionChangePacket.PACKET_ID -> ServerboundDimensionChangePacket.read(reader);
			case ServerboundChunkTimestampsRequestPacket.PACKET_ID -> ServerboundChunkTimestampsRequestPacket.read(reader);
			case ServerboundCatchupRequestPacket.PACKET_ID -> ServerboundCatchupRequestPacket.read(reader);
			case ChunkTilePacket.PACKET_ID -> ChunkTilePacket.read(reader);
			default -> throw new UnexpectedPacketException((byte) packetId);
		};
	}

	public static void encodePacket(
		final @NotNull BufferWriter writer,
		final @NotNull Packet packet
	) throws Exception {
		writer.writeUnt8(switch (packet) {
			case ChunkTilePacket $ -> ChunkTilePacket.PACKET_ID;
			case ServerboundHandshakePacket $ -> ServerboundHandshakePacket.PACKET_ID;
			case ServerboundIdentityResponsePacket $ -> ServerboundIdentityResponsePacket.PACKET_ID;
			case ServerboundDimensionChangePacket $ -> ServerboundDimensionChangePacket.PACKET_ID;
			case ServerboundChunkTimestampsRequestPacket $ -> ServerboundChunkTimestampsRequestPacket.PACKET_ID;
			case ServerboundCatchupRequestPacket $ -> ServerboundCatchupRequestPacket.PACKET_ID;
			case ClientboundIdentityRequestPacket $ -> ClientboundIdentityRequestPacket.PACKET_ID;
			case ClientboundWelcomePacket $ -> ClientboundWelcomePacket.PACKET_ID;
			case ClientboundRegionTimestampsPacket $ -> ClientboundRegionTimestampsPacket.PACKET_ID;
			case ClientboundChunkTimestampsResponsePacket $ -> ClientboundChunkTimestampsResponsePacket.PACKET_ID;
			default -> throw new UnexpectedPacketException(packet);
		});
		packet.write(writer);
	}
}
