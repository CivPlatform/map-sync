package gjum.minecraft.mapsync.common.integration;

import gjum.minecraft.mapsync.common.data.ChunkTile;
import journeymap.client.JourneymapClient;
import journeymap.client.io.FileHandler;
import journeymap.client.model.MapType;
import journeymap.client.model.RegionCoord;
import journeymap.common.nbt.RegionData;
import journeymap.common.nbt.RegionDataStorageHandler;

import static gjum.minecraft.mapsync.common.Utils.mc;

public class JourneyMapHelper {
	public static boolean isJourneyMapNotAvailable;

	static {
		try {
			Class.forName("journeymap.client.JourneymapClient");
			isJourneyMapNotAvailable = false;
		} catch (NoClassDefFoundError | ClassNotFoundException ignored) {
			isJourneyMapNotAvailable = true;
		}
	}

	public static boolean isMapping() {
		if (isJourneyMapNotAvailable) return false;
		return JourneymapClient.getInstance().isMapping();
	}

	public static boolean updateWithChunkTile(ChunkTile chunkTile) {
		if (isJourneyMapNotAvailable) return false;
		if (!JourneymapClient.getInstance().isMapping()) return false; // BaseMapTask does this

		var renderController = JourneymapClient.getInstance().getChunkRenderController();
		if (renderController == null) return false;

		var chunkMd = new TileChunkMD(chunkTile);

		var rCoord = RegionCoord.fromChunkPos(
				FileHandler.getJMWorldDir(mc),
				MapType.day(chunkTile.dimension()), // type doesn't matter, only dimension is used
				chunkMd.getCoord().x,
				chunkMd.getCoord().z);

		var key = new RegionDataStorageHandler.Key(rCoord, MapType.day(chunkTile.dimension()));
		RegionData regionData = RegionDataStorageHandler.getInstance().getRegionData(key);

		final boolean renderedDay = renderController.renderChunk(rCoord,
				MapType.day(chunkTile.dimension()), chunkMd, regionData);
		if (!renderedDay) System.out.println("Failed rendering day at " + chunkTile.chunkPos());

		final boolean renderedBiome = renderController.renderChunk(rCoord,
				MapType.biome(chunkTile.dimension()), chunkMd, regionData);
		if (!renderedBiome) System.out.println("Failed rendering biome at " + chunkTile.chunkPos());

		final boolean renderedTopo = renderController.renderChunk(rCoord,
				MapType.topo(chunkTile.dimension()), chunkMd, regionData);
		if (!renderedTopo) System.out.println("Failed rendering topo at " + chunkTile.chunkPos());

		return renderedDay && renderedBiome && renderedTopo;
	}
}
