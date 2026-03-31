package gjum.minecraft.mapsync.mod.net.auth;

import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public final class AuthStateHolder {
	public AuthState ref;

	public AuthState get() {
		synchronized (this) {
			return this.ref;
		}
	}

	public AuthState set(
		final AuthState replacement
	) {
		synchronized (this) {
			final AuthState previous = this.ref;
			this.ref = replacement;
			return previous;
		}
	}

	public boolean setIf(
		final @NotNull Predicate<AuthState> currentValue,
		final @NotNull Supplier<AuthState> nextValue
	) {
		synchronized (this) {
			final AuthState value = this.ref;
			if (!currentValue.test(value)) {
				return false;
			}
			this.ref = nextValue.get();
			return true;
		}
	}
}
