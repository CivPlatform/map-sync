package gjum.minecraft.mapsync.mod.data;

import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record BlockInfo(int y, BlockState state) {
	public void write(BufferWriter writer) throws Exception {
		writer.writeInt16((short) y);
		writer.writeUnt16(Block.BLOCK_STATE_REGISTRY.getId(state)); // we can assume this never becomes large enough to overflow
	}

	public static BlockInfo read(BufferReader reader) throws Exception {
		int y = reader.readInt16();
		int stateId = reader.readUnt16();
		return new BlockInfo(y, Block.BLOCK_STATE_REGISTRY.byId(stateId));
	}
}
