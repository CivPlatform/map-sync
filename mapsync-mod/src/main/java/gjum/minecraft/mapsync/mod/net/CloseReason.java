package gjum.minecraft.mapsync.mod.net;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public sealed interface CloseReason {
	public record Error(
		@NotNull Throwable thrown
	) implements CloseReason {
		public Error {
			Objects.requireNonNull(thrown);
		}
	}

	public record Closed(
		int statusCode,
		String reason
	) implements CloseReason {}
}
