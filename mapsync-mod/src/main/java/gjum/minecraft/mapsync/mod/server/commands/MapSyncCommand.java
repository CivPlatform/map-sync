package gjum.minecraft.mapsync.mod.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import gjum.minecraft.mapsync.mod.server.MapSyncServerState;
import gjum.minecraft.mapsync.mod.server.config.Whitelist;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.NotNull;

/// Implements `/mapsync` subcommands available to operators on the dedicated
/// server. Today: status + whitelist add/remove/list/reload. List- and kick-
/// connected-clients are deferred until Phase 2E adds the websocket server.
public final class MapSyncCommand {
	private static final SimpleCommandExceptionType NO_STATE =
		new SimpleCommandExceptionType(Component.literal("MapSync state is not loaded yet"));
	private static final SimpleCommandExceptionType UNKNOWN_PLAYER =
		new SimpleCommandExceptionType(Component.literal("No UUID known for that name (player has not joined yet)"));

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
		);
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
		ctx.getSource().sendSuccess(() -> Component.literal(
			"MapSync state: dir=" + state.dataDir()
				+ "; port=" + state.config().port
				+ "; auth=" + state.config().auth
				+ "; whitelist=" + state.config().whitelist
				+ " (" + state.whitelist().size() + " entries)"
				+ "; uuidCache=" + state.uuidCache().size() + " entries"
				+ "; websocket=not-yet-implemented"
		), false);
		return 1;
	}

	private static int handleWhitelistList(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		final int size = state.whitelist().size();
		ctx.getSource().sendSuccess(() -> Component.literal(
			"MapSync whitelist: " + size + " entries (see " + state.dataDir().resolve("whitelist.json") + ")"
		), false);
		return size;
	}

	private static int handleWhitelistAdd(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		final String input = StringArgumentType.getString(ctx, "player");
		final UUID uuid = resolvePlayer(state, input);
		final boolean added = state.whitelist().add(uuid);
		if (!added) {
			ctx.getSource().sendSuccess(() -> Component.literal("Already whitelisted: " + uuid), false);
			return 0;
		}
		try {
			state.whitelist().save(state.dataDir().resolve("whitelist.json"));
		}
		catch (final Exception e) {
			ctx.getSource().sendFailure(Component.literal("Whitelist saved in memory but failed to persist: " + e.getMessage()));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Whitelisted " + uuid), true);
		return 1;
	}

	private static int handleWhitelistRemove(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		final String input = StringArgumentType.getString(ctx, "player");
		final UUID uuid = resolvePlayer(state, input);
		final boolean removed = state.whitelist().remove(uuid);
		if (!removed) {
			ctx.getSource().sendSuccess(() -> Component.literal("Was not on the whitelist: " + uuid), false);
			return 0;
		}
		try {
			state.whitelist().save(state.dataDir().resolve("whitelist.json"));
		}
		catch (final Exception e) {
			ctx.getSource().sendFailure(Component.literal("Whitelist updated in memory but failed to persist: " + e.getMessage()));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Removed from whitelist: " + uuid), true);
		return 1;
	}

	private static int handleWhitelistReload(
		final @NotNull CommandContext<CommandSourceStack> ctx
	) throws CommandSyntaxException {
		final MapSyncServerState state = requireState();
		try {
			final Path file = state.dataDir().resolve("whitelist.json");
			final Whitelist reloaded = Whitelist.loadOrCreate(file);
			state.whitelist().replaceAll(reloaded.snapshot());
			state.importMinecraftAllowlist(ctx.getSource().getServer());
			ctx.getSource().sendSuccess(() -> Component.literal(
				"Reloaded MapSync whitelist (" + state.whitelist().size() + " entries)"
			), true);
			return state.whitelist().size();
		}
		catch (final Exception e) {
			ctx.getSource().sendFailure(Component.literal("Reload failed: " + e.getMessage()));
			return 0;
		}
	}
}
