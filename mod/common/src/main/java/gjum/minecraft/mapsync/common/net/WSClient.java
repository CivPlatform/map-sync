package gjum.minecraft.mapsync.common.net;

import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.client.Minecraft;
import org.apache.http.client.utils.URIBuilder;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// https://www.rfc-editor.org/rfc/rfc6455.html#section-7.4.1
public class WSClient extends WebSocketClient {
	private static final Minecraft mc = Minecraft.getInstance();

//	private static URI buildUri(@NotNull String wsUri, @NotNull String mcServerIp) throws URISyntaxException {
//		if (!wsUri.contains("://")) wsUri = "wss://" + wsUri;
//		URIBuilder b = new URIBuilder(wsUri);
//		b.addParameter("username", mc.getUser().getName()); // current Mojang name; for Mojang authentication
//		b.addParameter("mcServerIp", mcServerIp); // to prevent accidentally sending map data of a different server
//		b.addParameter("version", MOD_VERSION);
//		return b.build();
//	}

	public WSClient(@NotNull URI wsUri) {
		super(wsUri);
		connect();
	}

	@Override
	public void onError(Exception e) {
		if (e instanceof ConnectException) return; // ignore
		e.printStackTrace();
		// if the error is fatal then onClose will be called additionally
	}

	@Override
	public void onClose(int code, String reason, boolean byRemote) {
		// TODO parent.handleClosed(code, reason, byRemote);
	}

	@Override
	public void onOpen(ServerHandshake handshakeData) {
		// TODO parent.handleConnected();
	}

	@Override
	public void onMessage(String message) {
		// TODO parse+handle packets
//		final ProtocolPacket packet = ProtocolPacket.packetFromJson(message);
//		if (packet instanceof ProtocolLoginChallenge) {
//			answerLoginChallenge((ProtocolLoginChallenge) packet);
//		} else {
//			parent.receivePacket(packet);
//		}
	}

//	private void answerLoginChallenge(ProtocolLoginChallenge challenge) {
//		try {
//			String secret = challenge.sessionId + " " + getURI().getHost();
//			MessageDigest digest = MessageDigest.getInstance("SHA-1");
//			digest.update(secret.getBytes());
//			String sha = new BigInteger(digest.digest()).toString(16);
//
//			mc.getMinecraftSessionService().joinServer(
//					mc.getUser().getGameProfile(),
//					mc.getUser().getAccessToken(),
//					sha);
//			LOGGER.info("[MapSync WS] Joined Mojang session");
//			send("{\"msgType\":\"AuthJoined\"}");
//		} catch (AuthenticationException | NoSuchAlgorithmException e) {
//			e.printStackTrace();
//			close(CloseFrame.GOING_AWAY, "MC session auth failed");
//		}
//	}
}
