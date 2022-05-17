package gjum.minecraft.mapsync.common.net;

import com.mojang.authlib.exceptions.AuthenticationException;
import gjum.minecraft.mapsync.common.MapSyncMod;
import gjum.minecraft.mapsync.common.net.encryption.EncryptionDecoder;
import gjum.minecraft.mapsync.common.net.encryption.EncryptionEncoder;
import gjum.minecraft.mapsync.common.net.packet.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.security.*;
import java.util.concurrent.ThreadLocalRandom;

public class ClientHandler extends ChannelInboundHandlerAdapter {
	private final TcpClient client;

	public ClientHandler(TcpClient client) {
		this.client = client;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object packet) {
		if (!client.isEncrypted()) {
			if (packet instanceof SEncryptionRequest) {
				setupEncryption(ctx, (SEncryptionRequest) packet);
			} else throw new Error("Expected encryption request, got " + packet);
		} else if (packet instanceof ChunkTilePacket) {
			MapSyncMod.INSTANCE.handleSharedChunk(((ChunkTilePacket) packet).chunkTile);
		} else throw new Error("Expected packet, got " + packet);
	}

	private void setupEncryption(ChannelHandlerContext ctx, SEncryptionRequest packet) {
		try {
			PublicKey key = packet.publicKey;

			byte[] sharedSecret = new byte[16];
			ThreadLocalRandom.current().nextBytes(sharedSecret);

			String sha;
			try {
				MessageDigest digest = MessageDigest.getInstance("SHA-1");
				digest.update(sharedSecret);
				digest.update(key.getEncoded());
				sha = new BigInteger(digest.digest()).toString(16);
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}

			User session = Minecraft.getInstance().getUser();
			Minecraft.getInstance().getMinecraftSessionService().joinServer(
					session.getGameProfile(), session.getAccessToken(), sha);

			try {
				ctx.channel().writeAndFlush(new CEncryptionResponse(
						encrypt(key, sharedSecret),
						encrypt(key, packet.verifyToken)));
			} catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException |
			         IllegalBlockSizeException e) {
				client.shutDown();
				throw new RuntimeException(e);
			}

			SecretKey secretKey = new SecretKeySpec(sharedSecret, "AES");
			ctx.pipeline()
					.addFirst("encrypt", new EncryptionEncoder(secretKey))
					.addFirst("decrypt", new EncryptionDecoder(secretKey));

			client.handleEncryptionSuccess();
		} catch (AuthenticationException e) {
			TcpClient.logger.warn("Auth error: " + e.getMessage(), e);
		}
	}

	private static byte[] encrypt(PublicKey key, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		return cipher.doFinal(data);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (cause instanceof IOException && "Connection reset by peer".equals(cause.getMessage())) return;
		if (cause instanceof ConnectException && cause.getMessage().startsWith("Connection refused: ")) return;

		TcpClient.logger.info("[map-sync] Network Error: " + cause);
		cause.printStackTrace();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		client.handleDisconnect(new RuntimeException("Channel inactive"));
		super.channelInactive(ctx);
	}
}
