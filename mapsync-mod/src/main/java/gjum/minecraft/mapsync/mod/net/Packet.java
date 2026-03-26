package gjum.minecraft.mapsync.mod.net;

import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

public interface Packet {
	public default void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		throw new NotImplementedException();
	}
}
