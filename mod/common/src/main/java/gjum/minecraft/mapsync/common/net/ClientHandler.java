package gjum.minecraft.mapsync.common.net;

import gjum.minecraft.mapsync.common.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.common.net.packet.SCatchup;
import gjum.minecraft.mapsync.common.net.packet.SEncryptionRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.net.ConnectException;

import static gjum.minecraft.mapsync.common.MapSyncMod.getMod;

/**
 * tightly coupled to {@link TcpClient}
 */
public class ClientHandler extends ChannelInboundHandlerAdapter {
	private final TcpClient client;

	public ClientHandler(TcpClient client) {
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
			} else if (packet instanceof SCatchup){
				getMod().handleCatchupData(((SCatchup) packet).last_timestamps);
			}

			else throw new Error("Expected packet, got " + packet);
		} catch (Throwable err) {
			err.printStackTrace();
			ctx.close();
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable err) throws Exception {
		if (err instanceof IOException && "Connection reset by peer".equals(err.getMessage())) return;
		if (err instanceof ConnectException && err.getMessage().startsWith("Connection refused: ")) return;

		TcpClient.logger.info("[map-sync] Network Error: " + err);
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
