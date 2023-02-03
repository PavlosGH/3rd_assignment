package cubyz.world;

import java.util.ArrayList;

import cubyz.multiplayer.Protocols;
import cubyz.multiplayer.server.Server;
import cubyz.multiplayer.server.User;
import cubyz.utils.Logger;
import cubyz.world.blocks.BlockEntity;
import cubyz.world.blocks.Blocks;
import cubyz.world.blocks.Updateable;

/**
 * A chunk of chunks.
 */

public class MetaChunk {
	public static final int metaChunkShift = 4;
	public static final int metaChunkShift2 = 2*metaChunkShift;
	public static final int metaChunkSize = 1 << metaChunkShift;
	public final int wx, wy, wz;
	public final NormalChunk[] chunks;
	public final ServerWorld world;
	public MetaChunk(int wx, int wy, int wz, ServerWorld world) {
		this.wx = wx;
		this.wy = wy;
		this.wz = wz;
		chunks = new NormalChunk[metaChunkSize*metaChunkSize*metaChunkSize];
		this.world = world;
	}
	
	public void save() {
		for(NormalChunk chunk : chunks) {
			if (chunk != null)
				chunk.save();
		}
	}
	
	public void clean() {
		for(NormalChunk chunk : chunks) {
			if (chunk != null)
				chunk.clean();
		}
	}
	
	public void updateBlockEntities() {
		for (NormalChunk ch : chunks) {
			if (ch != null && ch.isGenerated() && !ch.getBlockEntities().isEmpty()) {
				BlockEntity[] blockEntities = ch.getBlockEntities().toArray(new BlockEntity[0]);
				for (BlockEntity be : blockEntities) {
					if (be == null) break; // end of array
					if (be instanceof Updateable) {
						Updateable tk = (Updateable) be;
						tk.update(false);
						if (tk.randomUpdates()) {
							// TODO: Random ticks.
							/*if (rnd.nextInt(5) < 1) { // 1/5 chance
								tk.update(true);
							}*/
						}
					}
				}
			}
		}
	}
	
	public void liquidUpdate() {
		int[] neighbors = new int[6];
		for (NormalChunk ch : chunks) {
			if (ch == null) continue;
			int wx = ch.wx;
			int wy = ch.wy;
			int wz = ch.wz;
			if (ch.isGenerated() && !ch.getLiquids().isEmpty()) {
				int[] liquids = ch.getUpdatingLiquids().array;
				int size = ch.getUpdatingLiquids().size;
				ch.getUpdatingLiquids().clear();
				for (int j = 0; j < size; j++) {
					int block = ch.getBlockAtIndex(liquids[j]);
					int bx = liquids[j]/Chunk.getIndex(1, 0, 0) & Chunk.chunkMask;
					int by = liquids[j]/Chunk.getIndex(0, 1, 0) & Chunk.chunkMask;
					int bz = liquids[j]/Chunk.getIndex(0, 0, 1) & Chunk.chunkMask;
					ch.getNeighbors(bx, by, bz, neighbors);
					for (int i = 0; i < 6; i++) {
						if(i == Neighbors.DIR_UP) continue;
						int b = neighbors[i];
						if (b == 0) {
							int dx = Neighbors.REL_X[i];
							int dy = Neighbors.REL_Y[i];
							int dz = Neighbors.REL_Z[i];
							if (dy == -1 || (neighbors[Neighbors.DIR_DOWN] != 0 && Blocks.blockClass(neighbors[Neighbors.DIR_DOWN]) != Blocks.BlockClass.FLUID)) {
								ch.addBlockPossiblyOutside(block, wx+bx+dx, wy+by+dy, wz+bz+dz, true);
								for(User user : Server.users) { // Send the liquid update to all players:
									Protocols.BLOCK_UPDATE.send(user, wx+bx+dx, wy+by+dy, wz+bz+dz, block);
								}
							}
						}
					}
				}
			}
		}
	}
	
	public void update(int entityDistance, ArrayList<NormalChunk> chunksList) {
		// Shift the player position, so chunks are loaded once the center comes into render distance:
		int edSquare = entityDistance*entityDistance << Chunk.chunkShift2;
		for(int px = 0; px < metaChunkSize; px++) {
			int wx = px*Chunk.chunkSize + this.wx;
			for(int py = 0; py < metaChunkSize; py++) {
				int wy = py*Chunk.chunkSize + this.wy;
				for(int pz = 0; pz < metaChunkSize; pz++) {
					int wz = pz*Chunk.chunkSize + this.wz;
					boolean isNeeded = false;
					for(User user : Server.users) {
						long dx = Math.max(0, Math.abs(wx - (int)user.player.getPosition().x + Chunk.chunkSize/2) - Chunk.chunkSize/2);
						long dy = Math.max(0, Math.abs(wy - (int)user.player.getPosition().y + Chunk.chunkSize/2) - Chunk.chunkSize/2);
						long dz = Math.max(0, Math.abs(wz - (int)user.player.getPosition().z + Chunk.chunkSize/2) - Chunk.chunkSize/2);
						long dist = dx*dx + dy*dy + dz*dz;
						if(dist < edSquare) {
							isNeeded = true;
							break;
						}
					}
					int index = (px << metaChunkShift) | (py <<  metaChunkShift2) | pz;
					NormalChunk chunk = chunks[index];
					if (!isNeeded) {
						if (chunk != null) {
							chunk.clean();
							chunks[index] = null;
						}
					} else if (chunk == null) {
						try {
							chunk = world.chunkManager.getNormalChunkFromCache(new ChunkData(wx, wy, wz, 1));
							if(chunk == null) { // Generate new chunk only if it wasn't found in the cache.
								chunk = new NormalChunk(world, wx, wy, wz);
								world.queueChunk(chunk, null);
							} else {
								chunk.unclean();
							}
							chunks[index] = chunk;
							chunksList.add(chunk);
						} catch (Exception e) {
							Logger.error(e);
						}
					} else {
						chunksList.add(chunk);
					}
				}
			}
		}
	}
	
	public NormalChunk getChunk(int wx, int wy, int wz) {
		int cx = (wx - this.wx) >> Chunk.chunkShift;
		int cy = (wy - this.wy) >> Chunk.chunkShift;
		int cz = (wz - this.wz) >> Chunk.chunkShift;
		int index = (cx << metaChunkShift) | (cy <<  metaChunkShift2) | cz;
		return chunks[index];
	}
}
