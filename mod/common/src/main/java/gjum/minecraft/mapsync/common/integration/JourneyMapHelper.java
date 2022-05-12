package gjum.minecraft.mapsync.common.integration;

import gjum.minecraft.mapsync.common.protocol.BlockInfo;
import gjum.minecraft.mapsync.common.protocol.ChunkTile;
import journeymap.client.JourneymapClient;
import journeymap.client.io.FileHandler;
import journeymap.client.model.*;
import journeymap.common.nbt.RegionData;
import journeymap.common.nbt.RegionDataStorageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.chunk.LevelChunk;

import static gjum.minecraft.mapsync.common.Utils.getBiomeRegistry;
import static gjum.minecraft.mapsync.common.Utils.mc;

public class JourneyMapHelper {
	public static void updateWithChunkTile(ChunkTile chunkTile) {
		var chunkMd = jmNBTChunkMDFromChunkTile(chunkTile);

		MapType mapType = MapType.day(chunkTile.dimension());

		var rCoord = RegionCoord.fromChunkPos(
				FileHandler.getJMWorldDir(mc),
				mapType,
				chunkMd.getCoord().x,
				chunkMd.getCoord().z);

		var key = new RegionDataStorageHandler.Key(rCoord, mapType);
		RegionData regionData = RegionDataStorageHandler.getInstance().getRegionData(key);

		var renderController = JourneymapClient.getInstance().getChunkRenderController();

		final boolean rendered = renderController.renderChunk(rCoord, mapType, chunkMd, regionData);
	}

	public static NBTChunkMD jmNBTChunkMDFromChunkTile(ChunkTile chunkTile) {
		var biomeRegistry = getBiomeRegistry();
		var data = new CompoundTag();
		int colNr = 0;
		int cx0 = chunkTile.chunkPos().x * 16;
		int cz0 = chunkTile.chunkPos().z * 16;
		// "x/z in chunk"
		for (int zic = 0; zic < 16; zic++) {
			for (int xic = 0; xic < 16; xic++) {
				String colKey = (cx0 + xic) + "," + (cz0 + zic);

				var col = chunkTile.columns()[colNr++];
				var colTag = new CompoundTag();

				int topY = col.layers().get(0).y();
				colTag.putInt("top_y", topY);

				String biomeName = biomeRegistry.getKey(col.biome(biomeRegistry)).toString();
				colTag.putString("biome_name", biomeName);

				var bsTag = new CompoundTag();
				for (BlockInfo layer : col.layers()) {
					var stateNbt = NbtUtils.writeBlockState(layer.state());
					bsTag.put(String.valueOf(layer.y()), stateNbt);
				}
				colTag.put("blockstates", bsTag);

				data.put(colKey, colTag);
			}
		}

		MapType mapType = MapType.day(chunkTile.dimension());
		LevelChunk mcChunk = new LevelChunk(Minecraft.getInstance().level, chunkTile.chunkPos());
		return new NBTChunkMD(mcChunk, chunkTile.chunkPos(), data, mapType);
	}
}
