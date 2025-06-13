package gjum.minecraft.mapsync.mod.net;

import static gjum.minecraft.mapsync.mod.MapSyncMod.debugLog;
import static gjum.minecraft.mapsync.mod.MapSyncMod.getMod;

import com.mojang.authlib.exceptions.AuthenticationException;
import gjum.minecraft.mapsync.mod.MapSyncMod;
import gjum.minecraft.mapsync.mod.data.ChunkTile;
import gjum.minecraft.mapsync.mod.net.encryption.EncryptionDecoder;
import gjum.minecraft.mapsync.mod.net.encryption.EncryptionEncoder;
import gjum.minecraft.mapsync.mod.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundEncryptionRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundEncryptionResponsePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundHandshakePacket;
import gjum.minecraft.mapsync.mod.utils.Shortcuts;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * handles reconnection, authentication, encryption
 */
public class SyncClient {
	private final HashMap<ChunkPos, byte[]> serverKnownChunkHashes = new HashMap<>();

	public synchronized void sendChunkTile(ChunkTile chunkTile) {
		var serverKnownHash = getServerKnownChunkHash(chunkTile.chunkPos());
		if (Arrays.equals(chunkTile.dataHash(), serverKnownHash)) {
			debugLog("server already has chunk (hash) " + chunkTile.chunkPos());
			return; // server already has this chunk
		}

		send(new ChunkTilePacket(chunkTile));

		// assume packet will reach server eventually
		setServerKnownChunkHash(chunkTile.chunkPos(), chunkTile.dataHash());
	}

	public synchronized byte[] getServerKnownChunkHash(ChunkPos chunkPos) {
		return serverKnownChunkHashes.get(chunkPos);
	}

	public synchronized void setServerKnownChunkHash(ChunkPos chunkPos, byte[] hash) {
		serverKnownChunkHashes.put(chunkPos, hash);
	}

	// XXX end of hotfix

	public static final Logger logger = LogManager.getLogger(SyncClient.class);

	public int retrySec = 5;

	public final @NotNull String address;
	public final @NotNull String gameAddress;

	/**
	 * false = don't auto-reconnect but maintain connection as long as it stays up.
	 * can be set to true again later.
	 */
	public boolean autoReconnect = true;
	/**
	 * false = don't reconnect under any circumstances,
	 * and disconnect when coming across this during a check
	 */
	public boolean isShutDown = false;
	private boolean isEncrypted = false;
	private @Nullable String lastError;
	/**
	 * limited (on insert) to 199 entries
	 */
	private ArrayList<Packet> queue = new ArrayList<>();
	private @Nullable Channel channel;
	private static @Nullable NioEventLoopGroup workerGroup;

	public SyncClient(@NotNull String address, @NotNull String gameAddress) {
		if (!address.contains(":")) address = address + ":12312";
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
				public void initChannel(SocketChannel ch) {
					ch.pipeline().addLast(
							new LengthFieldPrepender(4),
							new LengthFieldBasedFrameDecoder(1 << 20, 0, 4, 0, 4),
							new ClientboundPacketDecoder(),
							new ServerboundPacketEncoder(),
							new ClientHandler(SyncClient.this));
				}
			});

			String[] hostPortArr = address.split(":");
			int port = Integer.parseInt(hostPortArr[1]);

			final var channelFuture = bootstrap.connect(hostPortArr[0], port);
			channel = channelFuture.channel();
			channelFuture.addListener(future -> {
				if (future.isSuccess()) {
					logger.info("[map-sync] Connected to " + address);
					channelFuture.channel().writeAndFlush(new ServerboundHandshakePacket(
							getMod().getVersion(),
							Minecraft.getInstance().getUser().getName(),
							gameAddress,
							getMod().getDimensionState().dimension.identifier().toString()));
				} else {
					handleDisconnect(future.cause());
				}
			});
		} catch (Throwable e) {
			e.printStackTrace();
			handleDisconnect(e);
		}
	}

	void handleDisconnect(Throwable err) {
		isEncrypted = false;

		if (Minecraft.getInstance().level == null) shutDown();

		String errMsg = err.getMessage();
		if (errMsg == null) errMsg = err.toString();
		lastError = errMsg;
		if (isShutDown) {
			logger.warn("[map-sync] Got disconnected from '" + address + "'." +
					" Won't retry (has shut down)");
			if (!errMsg.contains("Channel inactive")) err.printStackTrace();
		} else if (!autoReconnect) {
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
		if (channel == null) return;

		lastError = null;
		isEncrypted = true;
		getMod().handleSyncServerEncryptionSuccess();

		for (Packet packet : queue) {
			channel.write(packet);
		}
		queue.clear();
		channel.flush();
	}

	public boolean isEncrypted() {
		return isEncrypted;
	}

	public String getError() {
		return lastError;
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
				// don't let the queue occupy too much memory
				if (queue.size() > 200) {
					logger.warn("[map-sync] Dropping 100 oldest packets from queue");
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

	void setUpEncryption(ChannelHandlerContext ctx, ClientboundEncryptionRequestPacket packet) {
		byte[] sharedSecret = new byte[16];
		ThreadLocalRandom.current().nextBytes(sharedSecret);

		if (!MapSyncMod.getMod().isDevMode()) {
			// note that this is different from minecraft (we get no negative hashes)
			final String shaHex; {
				final MessageDigest md = Shortcuts.shaHash();
				md.update(sharedSecret);
				md.update(packet.publicKey().getEncoded());
				shaHex = HexFormat.of().formatHex(md.digest());
			}

			final User session = Minecraft.getInstance().getUser();
			try {
				Minecraft.getInstance().services().sessionService().joinServer(
						session.getProfileId(),
						session.getAccessToken(),
						shaHex
				);
			} catch (AuthenticationException e) {
				SyncClient.logger.warn("Auth error (probably cracked): " + e.getMessage());
			}
		}

		try {
			ctx.channel().writeAndFlush(new ServerboundEncryptionResponsePacket(
					encrypt(packet.publicKey(), sharedSecret),
					encrypt(packet.publicKey(), packet.verifyToken())));
		} catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException |
				 IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
			shutDown();
			throw new RuntimeException(e);
		}

		SecretKey secretKey = new SecretKeySpec(sharedSecret, "AES");
		ctx.pipeline()
				.addFirst("encrypt", new EncryptionEncoder(secretKey))
				.addFirst("decrypt", new EncryptionDecoder(secretKey));

		handleEncryptionSuccess();
	}

	private static byte[] encrypt(PublicKey key, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, InvalidAlgorithmParameterException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
		// https://docs.openssl.org/master/man3/RSA_public_encrypt/#description
		cipher.init(Cipher.ENCRYPT_MODE, key, new OAEPParameterSpec(
			"SHA-256",
			"MGF1",
			new MGF1ParameterSpec("SHA-256"),
			PSource.PSpecified.DEFAULT
		));
		return cipher.doFinal(data);
	}
}
