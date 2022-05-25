package gjum.minecraft.mapsync.common.data;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

public record CatchupChunk(
        int chunk_x, int chunk_z,
        long timestamp
) {

    public static List<CatchupChunk> fromBytes(ByteBuf buf){

        List<CatchupChunk> chunks = new ArrayList<>();

        while (buf != null && !buf.isReadable()){
            int chunk_x = buf.readInt();
            int chunk_z = buf.readInt();
            long timestamp = buf.readLong();
            CatchupChunk chunk = new CatchupChunk(chunk_x, chunk_z, timestamp);
            chunks.add(chunk);
        }

        return chunks;

    }



}
