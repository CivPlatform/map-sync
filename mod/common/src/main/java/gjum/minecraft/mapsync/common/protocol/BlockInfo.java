package gjum.minecraft.mapsync.common.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record BlockInfo(int y, BlockState state) {
	public void write(ByteBuf buf) {
		buf.writeShort(y);
		buf.writeShort(Block.BLOCK_STATE_REGISTRY.getId(state)); // we can assume this never becomes large enough to overflow
	}

	public static BlockInfo fromBuf(ByteBuf in) {
		int y = in.readShort();
		int stateId = in.readUnsignedShort();
		return new BlockInfo(y, Block.BLOCK_STATE_REGISTRY.byId(stateId));
	}
}
