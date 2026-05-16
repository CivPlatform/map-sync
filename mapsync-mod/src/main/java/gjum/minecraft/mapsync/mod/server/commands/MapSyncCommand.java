package gjum.minecraft.mapsync.mod.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import gjum.minecraft.mapsync.mod.server.MapSyncServerState;
import gjum.minecraft.mapsync.mod.server.config.Whitelist;
import gjum.minecraft.mapsync.mod.server.net.MapSyncWsServer;
import gjum.minecraft.mapsync.mod.server.net.WsServerClient;
import gjum.minecraft.mapsync.mod.server.net.auth.ServerAuthState;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.NotNull;

/// Implements `/mapsync` subcommands available to operators on the dedicated
/// server. Today: status + whitelist add/remove/list/reload. List- and kick-
/// connected-clients are deferred until Phase 2E adds the websocket server.
public final class MapSyncCommand {
	private static final SimpleCommandExceptionType NO_STATE =
		new SimpleCommandExceptionType(text("MapSync state is not loaded yet", ChatFormatting.RED));
	private static final SimpleCommandExceptionType UNKNOWN_PLAYER =
		new SimpleCommandExceptionType(text("No UUID known for that name (player has not joined yet)", ChatFormatting.RED));
	private static final SimpleCommandExceptionType NO_WS_SERVER =
		new SimpleCommandExceptionType(text("MapSync websocket server is not running", ChatFormatting.RED));
	private static final SimpleCommandExceptionType UNKNOWN_CLIENT =
		new SimpleCommandExceptionType(text("No connected MapSync client with that id", ChatFormatting.RED));

	private MapSyncCommand() {
	}

	public static void register(
		final @NotNull CommandDispatcher<CommandSourceStack> dispatcher,
		@SuppressWarnings("unused") final @NotNull CommandBuildContext buildContext,
		@SuppressWarnings("unused") final @NotNull Commands.CommandSelection environment
	) {
		dispatcher.register(
			Commands.literal("mapsync")
				.requires((src) -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
				.then(Commands.literal("status")
					.executes(MapSyncCommand::handleStatus))
				.then(Commands.literal("whitelist")
					.then(Commands.literal("list")
						.executes(MapSyncCommand::handleWhitelistList))
					.then(Commands.literal("add")
						.then(Commands.argument("player", StringArgumentType.word())
							.executes(MapSyncCommand::handleWhitelistAdd)))
					.then(Commands.literal("remove")
						.then(Commands.argument("player", StringArgumentType.word())
							.executes(MapSyncCommand::handleWhitelistRemove)))
					.then(Commands.literal("reload")
						.executes(MapSyncCommand::handleWhitelistReload)))
				.then(Commands.literal("clients")
					.then(Commands.literal("list")
						.executes(MapSyncCommand::handleClientsList))
					.then(Commands.literal("kick")
						.then(Commands.argument("id", LongArgumentType.longArg(1))
							.executes(MapSyncCommand::handleClientsKick))))
		);
	}

	private static @NotNull MapSyncWsServer requireWsServer() throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		final MapSyncWsServer ws = state.wsServer();
		if (ws == null) {
			throw NO_WS_SERVER.create();
		}
		return ws;
	}

	private static @NotNull MapSyncServerState requireState() throws CommandSyntaxException {
		final MapSyncServerState state = MapSyncServerState.current();
		if (state == null) {
			throw NO_STATE.create();
		}
		return state;
	}

	private static @NotNull UUID resolvePlayer(
		final @NotNull MapSyncServerState state,
		final @NotNull String input
	) throws CommandSyntaxException {
		try {
			return UUID.fromString(input);
		}
		catch (final IllegalArgumentException ignored) {
		}
		final UUID cached = state.uuidCache().lookup(input);
		if (cached == null) {
			throw UNKNOWN_PLAYER.create();
		}
		return cached;
	}

	private static int handleStatus(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		final CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> header("MapSync status"), false);
		src.sendSuccess(() -> kv("data dir", state.dataDir().toString()), false);
		src.sendSuccess(() -> kv("port", Integer.toString(state.config().port)), false);
		src.sendSuccess(() -> kv("auth", state.config().auth).append(
			text("  ", ChatFormatting.DARK_GRAY)
		).append(kv("whitelist", state.config().whitelist)), false);
		src.sendSuccess(() -> kv("whitelist entries", Integer.toString(state.whitelist().size())), false);
		src.sendSuccess(() -> kv("uuid cache entries", Integer.toString(state.uuidCache().size())), false);
		final MapSyncWsServer ws = state.wsServer();
		if (ws != null) {
			final int clientCount = ws.activeClients().size();
			src.sendSuccess(() -> text("websocket: ", ChatFormatting.AQUA)
				.append(text("listening on " + ws.getAddress(), ChatFormatting.GREEN))
				.append(text("  (" + clientCount + " connected)", ChatFormatting.GRAY)), false);
		}
		else {
			src.sendSuccess(() -> text("websocket: ", ChatFormatting.AQUA)
				.append(text("not started", ChatFormatting.YELLOW)), false);
		}
		return 1;
	}

	private static int handleClientsList(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncWsServer ws = requireWsServer();
		final List<WsServerClient> clients = ws.activeClients().stream()
			.sorted(Comparator.comparingLong((WsServerClient c) -> c.id))
			.toList();
		final CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> header("MapSync clients")
			.append(text(" (" + clients.size() + ")", ChatFormatting.GRAY)), false);
		if (clients.isEmpty()) {
			src.sendSuccess(() -> text("  (none connected)", ChatFormatting.DARK_GRAY), false);
			return 0;
		}
		for (final WsServerClient client : clients) {
			final String authLabel;
			final ChatFormatting authColor;
			switch (client.auth) {
				case final ServerAuthState.Welcomed welcomed -> {
					authLabel = welcomed.name() + (welcomed.authed() ? " (authed)" : " (offline)");
					authColor = welcomed.authed() ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
				}
				case final ServerAuthState.AwaitingIdentityResponse $ -> {
					authLabel = "awaiting identity";
					authColor = ChatFormatting.YELLOW;
				}
				default -> {
					authLabel = "pre-handshake";
					authColor = ChatFormatting.GRAY;
				}
			}
			final String dim = client.dimension != null ? client.dimension.toString() : "—";
			final String addr = client.gameAddress != null ? client.gameAddress : "—";
			src.sendSuccess(() -> text("  #" + client.id + " ", ChatFormatting.DARK_GRAY)
				.append(text(authLabel, authColor))
				.append(text("  dim=", ChatFormatting.DARK_GRAY))
				.append(text(dim, ChatFormatting.WHITE))
				.append(text("  via=", ChatFormatting.DARK_GRAY))
				.append(text(addr, ChatFormatting.WHITE)), false);
		}
		return clients.size();
	}

	private static int handleClientsKick(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncWsServer ws = requireWsServer();
		final long targetId = LongArgumentType.getLong(ctx, "id");
		final WsServerClient target = ws.activeClients().stream()
			.filter((c) -> c.id == targetId)
			.findFirst()
			.orElseThrow(UNKNOWN_CLIENT::create);
		target.kick("operator-issued /mapsync clients kick");
		ctx.getSource().sendSuccess(() -> text("Kicked client #" + targetId, ChatFormatting.GREEN), true);
		return 1;
	}

	private static int handleWhitelistList(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		final Map<UUID, String> names = state.uuidCache().namesByUuid();
		final List<UUID> entries = state.whitelist().sortedSnapshot(
			(uuid) -> names.getOrDefault(uuid, uuid.toString())
		);
		final CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> header("MapSync whitelist")
			.append(text(" (" + entries.size() + ")", ChatFormatting.GRAY)), false);
		if (entries.isEmpty()) {
			src.sendSuccess(() -> text("  (empty — only ops + MC-whitelist sync in)", ChatFormatting.DARK_GRAY), false);
		}
		else {
			for (final UUID uuid : entries) {
				final String name = names.get(uuid);
				final MutableComponent line = text("  • ", ChatFormatting.DARK_GRAY);
				if (name != null) {
					line.append(text(name, ChatFormatting.WHITE))
						.append(text("  " + uuid, ChatFormatting.DARK_GRAY));
				}
				else {
					line.append(text(uuid.toString(), ChatFormatting.GRAY))
						.append(text("  (no cached name)", ChatFormatting.DARK_GRAY));
				}
				src.sendSuccess(() -> line, false);
			}
		}
		final Path file = state.dataDir().resolve("whitelist.json");
		src.sendSuccess(() -> muted("source: " + file), false);
		return entries.size();
	}

	private static int handleWhitelistAdd(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		final String input = StringArgumentType.getString(ctx, "player");
		final UUID uuid = resolvePlayer(state, input);
		final CommandSourceStack src = ctx.getSource();
		final boolean added = state.whitelist().add(uuid);
		if (!added) {
			src.sendSuccess(() -> text("Already whitelisted: ", ChatFormatting.YELLOW)
				.append(text(uuid.toString(), ChatFormatting.WHITE)), false);
			return 0;
		}
		try {
			state.whitelist().save(state.dataDir().resolve("whitelist.json"));
		}
		catch (final Exception e) {
			src.sendFailure(text("Whitelist saved in memory but failed to persist: " + e.getMessage(), ChatFormatting.RED));
			return 0;
		}
		final String name = state.uuidCache().namesByUuid().get(uuid);
		src.sendSuccess(() -> text("Whitelisted ", ChatFormatting.GREEN)
			.append(text(name != null ? name : uuid.toString(), ChatFormatting.WHITE))
			.append(name != null ? text("  " + uuid, ChatFormatting.DARK_GRAY) : Component.empty()), true);
		return 1;
	}

	private static int handleWhitelistRemove(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		final String input = StringArgumentType.getString(ctx, "player");
		final UUID uuid = resolvePlayer(state, input);
		final CommandSourceStack src = ctx.getSource();
		final boolean removed = state.whitelist().remove(uuid);
		if (!removed) {
			src.sendSuccess(() -> text("Was not on the whitelist: ", ChatFormatting.YELLOW)
				.append(text(uuid.toString(), ChatFormatting.WHITE)), false);
			return 0;
		}
		try {
			state.whitelist().save(state.dataDir().resolve("whitelist.json"));
		}
		catch (final Exception e) {
			src.sendFailure(text("Whitelist updated in memory but failed to persist: " + e.getMessage(), ChatFormatting.RED));
			return 0;
		}
		final String name = state.uuidCache().namesByUuid().get(uuid);
		src.sendSuccess(() -> text("Removed from whitelist: ", ChatFormatting.GREEN)
			.append(text(name != null ? name : uuid.toString(), ChatFormatting.WHITE))
			.append(name != null ? text("  " + uuid, ChatFormatting.DARK_GRAY) : Component.empty()), true);
		return 1;
	}

	private static int handleWhitelistReload(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		final CommandSourceStack src = ctx.getSource();
		try {
			final Path file = state.dataDir().resolve("whitelist.json");
			final Whitelist reloaded = Whitelist.loadOrCreate(file);
			state.whitelist().replaceAll(reloaded.snapshot());
			state.importMinecraftAllowlist(src.getServer());
			final int size = state.whitelist().size();
			src.sendSuccess(() -> text("Reloaded MapSync whitelist ", ChatFormatting.GREEN)
				.append(text("(" + size + " entries)", ChatFormatting.GRAY)), true);
			return size;
		}
		catch (final Exception e) {
			src.sendFailure(text("Reload failed: " + e.getMessage(), ChatFormatting.RED));
			return 0;
		}
	}

	// ============================================================
	// Formatting helpers
	// ============================================================

	private static @NotNull MutableComponent text(
		final @NotNull String value,
		final @NotNull ChatFormatting color
	) {
		return Component.literal(value).withStyle(color);
	}

	private static @NotNull MutableComponent header(
		final @NotNull String label
	) {
		return text("[MapSync] ", ChatFormatting.GOLD).append(text(label, ChatFormatting.YELLOW));
	}

	private static @NotNull MutableComponent kv(
		final @NotNull String key,
		final @NotNull String value
	) {
		return text(key + ": ", ChatFormatting.AQUA).append(text(value, ChatFormatting.WHITE));
	}

	private static @NotNull MutableComponent kv(
		final @NotNull String key,
		final boolean value
	) {
		return text(key + ": ", ChatFormatting.AQUA)
			.append(text(Boolean.toString(value), value ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	private static @NotNull MutableComponent muted(
		final @NotNull String value
	) {
		return text(value, ChatFormatting.DARK_GRAY);
	}
}
