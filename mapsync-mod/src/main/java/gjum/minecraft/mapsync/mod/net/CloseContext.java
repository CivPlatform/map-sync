package gjum.minecraft.mapsync.mod.net;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public interface CloseContext {
	// https://www.rfc-editor.org/rfc/rfc6455#section-7.4.1
	public static final int CLOSE_1000_NORMAL_CLOSE = 1000;
	public static final int CLOSE_1001_GOING_AWAY = 1001;
	public static final int CUSTOM_CLOSE_4000_ERROR = 4000;

	public record Error(
		@NotNull Throwable thrown
	) implements CloseContext {
		public Error {
			Objects.requireNonNull(thrown);
		}
	}

	public record Closed(
		int closeCode,
		String reason
	) implements CloseContext {}
}
