package gjum.minecraft.mapsync.common.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.List;

import static gjum.minecraft.mapsync.common.Utils.readStringFromBuf;
import static gjum.minecraft.mapsync.common.Utils.writeStringToBuf;

public record CatchupChunk (
        ResourceKey<Level>dimension,
        int chunk_x, int chunk_z,
        long timestamp
) {

    /** The list returned is sorted by latest update to earliest update. */
    public static List<CatchupChunk> fromBuf(ByteBuf buf){
        try{
            int length = buf.readInt();
            List<CatchupChunk> chunks = new ArrayList<>(length);

            while (!buf.isReadable()) {
                String world_str = readStringFromBuf(buf);
                var dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(world_str));
                int chunk_x = buf.readInt();
                int chunk_z = buf.readInt();
                long timestamp = buf.readLong();
                CatchupChunk chunk = new CatchupChunk(dimension, chunk_x, chunk_z, timestamp);
                chunks.add(chunk);
            }
            return chunks;

        } catch (BufferOverflowException e){
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void write(ByteBuf buf) {
        writeStringToBuf(buf, dimension.location().toString());
        buf.writeInt(chunk_x);
        buf.writeInt(chunk_z);
        buf.writeLong(timestamp);
    }

    /** calculates Euclidean distance between a different Chunk and another */
    public double getDistanceTo(ChunkPos other){
        ChunkPos current = chunkPos();
        return Math.sqrt(Math.pow(other.x - current.x, 2) + Math.pow(other.z - current.z, 2));
    }

    public ChunkPos chunkPos(){
        return new ChunkPos(chunk_x, chunk_z);
    }



}
