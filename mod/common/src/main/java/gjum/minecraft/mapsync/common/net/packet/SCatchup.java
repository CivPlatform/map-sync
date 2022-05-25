package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import java.util.List;


public class SCatchup extends Packet {

    public static final int PACKET_ID = 5;

    @Nonnull
    public final List<CatchupChunk> last_timestamps;

    public SCatchup(@Nonnull List<CatchupChunk> last_timestamps) {
        this.last_timestamps = last_timestamps;
    }

    public static Packet read(ByteBuf buf) {
        return new SCatchup(
                CatchupChunk.fromBytes(buf)
        );
    }

    @Override
    public void write(ByteBuf buf) {

    }
}
