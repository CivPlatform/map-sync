package gjum.minecraft.mapsync.common.net;

import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.net.packet.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.net.ConnectException;

import static gjum.minecraft.mapsync.common.MapSyncMod.getMod;

/**
 * tightly coupled to {@link SyncClient}
 */
public class ClientHandler extends ChannelInboundHandlerAdapter {
	private final SyncClient client;

	public ClientHandler(SyncClient client) {
		this.client = client;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object packet) {
		try {
			if (!client.isEncrypted()) {
				if (packet instanceof ClientboundEncryptionRequestPacket pktEncryptionRequest) {
					client.setUpEncryption(ctx, pktEncryptionRequest);
				} else throw new Error("Expected encryption request, got " + packet);
			} else if (packet instanceof ChunkTilePacket pktChunkTile) {
				getMod().handleSharedChunk(pktChunkTile.chunkTile);
			} else if (packet instanceof ClientboundRegionTimestampsPacket pktRegionTimestamps) {
				getMod().handleRegionTimestamps(pktRegionTimestamps, client);
			} else if (packet instanceof ClientboundChunkTimestampsResponsePacket pktCatchup) {
				for (CatchupChunk chunk : pktCatchup.chunks) {
					chunk.syncClient = this.client;
				}
				getMod().handleCatchupData((ClientboundChunkTimestampsResponsePacket) packet);
			} else throw new Error("Expected packet, got " + packet);
		} catch (Throwable err) {
			err.printStackTrace();
			ctx.close();
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable err) throws Exception {
		if (err instanceof IOException && "Connection reset by peer".equals(err.getMessage())) return;
		if (err instanceof ConnectException && err.getMessage().startsWith("Connection refused: ")) return;

		SyncClient.logger.info("[map-sync] Network Error: " + err);
		err.printStackTrace();
		ctx.close();
		super.exceptionCaught(ctx, err);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		client.handleDisconnect(new RuntimeException("Channel inactive"));
		super.channelInactive(ctx);
	}
}
