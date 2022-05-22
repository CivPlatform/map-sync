package gjum.minecraft.mapsync.common.net;

import gjum.minecraft.mapsync.common.net.packet.CHandshake;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static gjum.minecraft.mapsync.common.MapSyncMod.getMod;

public class TcpClient {
	public static final Logger logger = LogManager.getLogger(TcpClient.class);

	public int retrySec = 5;

	public final @NotNull String address;
	public final @NotNull String gameAddress;

	public boolean isShutDown = false;
	private boolean isEncrypted = false;
	private ArrayList<Packet> queue = new ArrayList<>();
	private @Nullable Channel channel;
	private static @Nullable NioEventLoopGroup workerGroup;

	public TcpClient(@NotNull String address, @NotNull String gameAddress) {
		if (address == null || address.trim().isEmpty() || !address.contains(":")) {
			throw new Error("Invalid address: '" + address + "'");
		}
		this.address = address;
		this.gameAddress = gameAddress;
		connect();
	}

	private void connect() {
		try {
			if (isShutDown) return;

			if (workerGroup != null && !workerGroup.isShuttingDown()) {
				// end any tasks of the old connection
				workerGroup.shutdownGracefully();
			}
			workerGroup = new NioEventLoopGroup();
			isEncrypted = false;

			var bootstrap = new Bootstrap();
			bootstrap.group(workerGroup);
			bootstrap.channel(NioSocketChannel.class);
			bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
			bootstrap.handler(new ChannelInitializer<SocketChannel>() {
				public void exceptionCaught(ChannelHandlerContext ctx, Throwable err) {
					// XXX handle expected exceptions
					err.printStackTrace();
					ctx.close();
				}

				public void initChannel(SocketChannel ch) {
					ch.pipeline().addLast(
							new LengthFieldPrepender(4),
							new LengthFieldBasedFrameDecoder(1 << 24, 0, 4, 0, 4),
							new ServerPacketDecoder(),
							new ClientPacketEncoder(),
							new ClientHandler(TcpClient.this));
				}
			});

			String[] hostPortArr = address.split(":");
			int port = Integer.parseInt(hostPortArr[1]);

			var channelFuture = bootstrap.connect(hostPortArr[0], port);
			channelFuture.addListener(future -> {
				if (!future.isSuccess()) {
					handleDisconnect(future.cause());
				}
			});

			// throw if connection error
			channelFuture.sync();

			logger.info("[map-sync] Connected to " + address);
			channel = channelFuture.channel();

			channel.writeAndFlush(new CHandshake(
					getMod().getVersion(),
					Minecraft.getInstance().getUser().getName(),
					gameAddress));

			getMod().handleSyncServerConnected();
		} catch (Throwable e) {
			if (e.getMessage() == null || !e.getMessage().startsWith("Connection refused: ")) { // reduce spam
				logger.error("[map-sync] Connection to '" + address + "' failed: " + e);
				e.printStackTrace();
			}
		}
	}

	void handleDisconnect(Throwable err) {
		isEncrypted = false;

		if (Minecraft.getInstance().level == null) shutDown();

		String errMsg = err.getMessage();
		if (errMsg == null) errMsg = "";
		if (isShutDown) {
			logger.warn("[map-sync] Got disconnected from '" + address + "'." +
					" Won't retry (autoReconnect=false)");
			if (!errMsg.contains("Channel inactive")) err.printStackTrace();
		} else if (workerGroup == null) {
			logger.warn("[map-sync] Got disconnected from '" + address + "'." +
					" Won't retry (workerGroup=null)");
			err.printStackTrace();
		} else {
			workerGroup.schedule(this::connect, retrySec, TimeUnit.SECONDS);
			if (!errMsg.startsWith("Connection refused: ")) { // reduce spam
				logger.warn("[map-sync] Got disconnected from '" + address + "'." +
						" Retrying in " + retrySec + " sec");
				if (!errMsg.contains("Channel inactive")) err.printStackTrace();
			}
		}
	}

	public synchronized void handleEncryptionSuccess() {
		isEncrypted = true;
		getMod().handleSyncServerEncryptionSuccess();

		for (Packet packet : queue) {
			channel.write(packet);
		}
		queue.clear();
		channel.flush();
	}

	boolean isConnected() {
		return channel != null && channel.isActive();
	}

	public boolean isEncrypted() {
		return isConnected() && isEncrypted;
	}

	/**
	 * Send if encrypted, or queue and send once encryption is set up.
	 */
	public void send(Packet packet) {
		send(packet, true);
	}

	/**
	 * Send if encrypted, or queue and send once encryption is set up.
	 */
	public synchronized void send(Packet packet, boolean flush) {
		try {
			if (isEncrypted() && channel != null && channel.isActive()) {
				if (flush) channel.writeAndFlush(packet);
				else channel.write(packet);
			} else {
				queue.add(packet);
				if (queue.size() > 200) {
					queue = queue.stream()
							.skip(100)
							.collect(Collectors.toCollection(ArrayList::new));
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public synchronized void shutDown() {
		isShutDown = true;
		if (channel != null) {
			channel.disconnect();
			channel.eventLoop().shutdownGracefully();
			channel = null;
		}
		if (workerGroup != null && !workerGroup.isShuttingDown()) {
			// this also stops any ongoing reconnect timeout
			workerGroup.shutdownGracefully();
			workerGroup = null;
		}
	}
}
