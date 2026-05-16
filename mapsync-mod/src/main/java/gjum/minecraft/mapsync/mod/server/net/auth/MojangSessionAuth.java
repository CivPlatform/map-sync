package gjum.minecraft.mapsync.mod.server.net.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/// Calls Mojang's `sessionserver.mojang.com/session/minecraft/hasJoined` to
/// confirm that a client legitimately holds the Minecraft account it claims
/// in its identity-response. Matches the algorithm the client's AuthProcess
/// uses on the join side: `serverId = sha1(serverSalt || clientSalt)` in
/// plain unsigned hex.
///
/// Blocking by design (single synchronous round-trip) — callers must run it
/// off the websocket worker thread so a slow Mojang endpoint can't stall
/// other connections.
public final class MojangSessionAuth {
	private static final @NotNull URI BASE =
		URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined");
	private static final @NotNull Duration TIMEOUT = Duration.ofSeconds(10);

	private final @NotNull HttpClient http;

	public MojangSessionAuth() {
		this.http = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.build();
	}

	public @NotNull Result hasJoined(
		final @NotNull String claimedUsername,
		final byte @NotNull [] serverSalt,
		final byte @NotNull [] clientSalt
	) throws Exception {
		final MessageDigest md = MessageDigest.getInstance("SHA-1");
		md.update(serverSalt);
		md.update(clientSalt);
		final String serverIdHex = HexFormat.of().formatHex(md.digest());

		final URI uri = URI.create(
			BASE + "?username=" + URLEncoder.encode(claimedUsername, StandardCharsets.UTF_8)
				+ "&serverId=" + serverIdHex
		);
		final HttpRequest req = HttpRequest.newBuilder(uri)
			.timeout(TIMEOUT)
			.header("User-Agent", "MapSync/server")
			.GET()
			.build();
		final HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());

		if (resp.statusCode() == 204) {
			throw new AuthException("Mojang refused login for " + claimedUsername + " (204 No Content)");
		}
		if (resp.statusCode() != 200) {
			throw new AuthException("Mojang hasJoined returned HTTP " + resp.statusCode());
		}
		final JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
		if (!json.has("name") || !json.has("id")) {
			throw new AuthException("Mojang hasJoined returned a malformed body");
		}
		final String name = json.get("name").getAsString();
		final UUID uuid = parseUndashedUuid(json.get("id").getAsString());
		return new Result(name, uuid);
	}

	/// Mojang returns UUIDs in the un-dashed 32-hex-character form. UUID.fromString
	/// requires dashes, so reinsert them at the standard positions.
	private static @NotNull UUID parseUndashedUuid(
		final @NotNull String raw
	) {
		if (raw.length() != 32) {
			return UUID.fromString(raw); // already dashed; let UUID validate
		}
		final String dashed = raw.substring(0, 8) + "-"
			+ raw.substring(8, 12) + "-"
			+ raw.substring(12, 16) + "-"
			+ raw.substring(16, 20) + "-"
			+ raw.substring(20);
		return UUID.fromString(dashed);
	}

	public record Result(@NotNull String name, @NotNull UUID uuid) {
	}

	public static final class AuthException extends Exception {
		public AuthException(final @NotNull String message) {
			super(message);
		}
	}
}
