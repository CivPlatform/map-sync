package gjum.minecraft.civ.mapsync.common;

import com.mojang.authlib.exceptions.AuthenticationException;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utils {
	private static final Minecraft mc = Minecraft.getInstance();

	private static HashMap<String, Long> lastTimeSeenError = new HashMap<>();

	public static void printErrorRateLimited(@NotNull Throwable e) {
		try {
			final long now = System.currentTimeMillis();
			final String key = e.getMessage();
			if (lastTimeSeenError.getOrDefault(key, 0L) > now - 10000L) return;
			lastTimeSeenError.put(key, now);
			e.printStackTrace();
		} catch (Throwable e2) {
			e2.printStackTrace();
		}
	}

	public static void joinServerMojangApi(String secret) throws AuthenticationException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			digest.update(secret.getBytes());
			String sha = new BigInteger(digest.digest()).toString(16);

			mc.getMinecraftSessionService().joinServer(
					mc.getUser().getGameProfile(),
					mc.getUser().getAccessToken(),
					sha);
		} catch (NoSuchAlgorithmException e) {
			throw new AuthenticationException(e);
		}
	}

	public static void writeStringToBuf(ByteBuf buf, @Nullable String string) {
		if (string == null) {
			buf.writeShort(0);
			return;
		}
		buf.writeShort(string.length());
		buf.writeCharSequence(string, UTF_8);
	}

	public static String readStringFromBuf(ByteBuf buf) {
		int strLen = buf.readUnsignedShort();
		return buf.readCharSequence(strLen, UTF_8).toString();
	}
}
