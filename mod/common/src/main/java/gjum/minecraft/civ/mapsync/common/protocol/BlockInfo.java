package gjum.minecraft.civ.mapsync.common.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record BlockInfo(int y, BlockState state) {
	public void write(ByteBuf buf) {
		buf.writeInt(y);
		buf.writeInt(Block.BLOCK_STATE_REGISTRY.getId(state));
	}

	public static BlockInfo fromBuf(ByteBuf in) {
		int y = in.readInt();
		int stateId = in.readInt(); // actually unsigned, but we can assume this never becomes large enough to overflow
		return new BlockInfo(y, Block.BLOCK_STATE_REGISTRY.byId(stateId));
	}
}
