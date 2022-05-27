package gjum.minecraft.mapsync.common.net;

import gjum.minecraft.mapsync.common.DimensionState;
import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.data.RegionPos;
import gjum.minecraft.mapsync.common.data.RegionTimestamp;
import gjum.minecraft.mapsync.common.net.packet.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
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
				if (packet instanceof SEncryptionRequest) {
					client.setUpEncryption(ctx, (SEncryptionRequest) packet);
				} else throw new Error("Expected encryption request, got " + packet);
			} else if (packet instanceof ChunkTilePacket) {
				getMod().handleSharedChunk(((ChunkTilePacket) packet).chunkTile);
			} else if (packet instanceof SRegionTimestamps timestamps) {
				DimensionState dimension = getMod().getDimensionState();
				if (dimension == null || !dimension.dimension.location().toString().equals(timestamps.getDimension())) {
					return;
				}
				RegionTimestamp[] regions = timestamps.getTimestamps();
				ShortArrayList list = new ShortArrayList();
				for (RegionTimestamp region : regions) {
					boolean requiresUpdate = dimension.requiresChunksFrom(new RegionPos(region.x(), region.z()), region.timestamp());
					if (requiresUpdate) {
						list.add(region.x());
						list.add(region.z());
					}
				}

				client.send(new CRegionCatchup(timestamps.getDimension(), list.toShortArray()));
			} else if (packet instanceof SCatchup) {
				for (CatchupChunk chunk : ((SCatchup) packet).chunks) {
					chunk.syncClient = this.client;
				}
				getMod().handleCatchupData((SCatchup) packet);
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
