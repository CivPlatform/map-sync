package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;

public class CCatchupRequest extends Packet {

    public static final int PACKET_ID = 6;
    public CatchupChunk chunk;

    public CCatchupRequest(@Nonnull CatchupChunk chunk){
        this.chunk = chunk;
    }

    @Override
    public void write(ByteBuf buf) {
        chunk.write(buf);
    }
}
