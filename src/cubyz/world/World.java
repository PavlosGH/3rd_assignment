package cubyz.world;

import cubyz.world.entity.ItemEntityManager;
import cubyz.world.save.BlockPalette;
import org.joml.Vector3d;
import org.joml.Vector3f;

import cubyz.api.CurrentWorldRegistries;
import cubyz.world.items.ItemStack;
import org.joml.Vector3i;

public abstract class World {
	public static final int DAY_CYCLE = 12000; // Length of one in-game day in 100ms. Midnight is at DAY_CYCLE/2. Sunrise and sunset each take about 1/16 of the day. Currently set to 20 minutes
	public static final float GRAVITY = 9.81F*1.5F;

	public ItemEntityManager itemEntityManager;
	public BlockPalette blockPalette;

	protected boolean generated;

	public long gameTime;
	protected long milliTime;
	protected long lastUpdateTime = System.currentTimeMillis();
	protected boolean doGameTimeCycle = true;
	
	protected long seed;

	protected final String name;
	
	public CurrentWorldRegistries registries;

	public final Vector3i spawn = new Vector3i(0, Integer.MIN_VALUE, 0);
	
	public World(String name) {
		this.name = name;

		milliTime = System.currentTimeMillis();
	}

	public void setGameTimeCycle(boolean value)
	{
		doGameTimeCycle = value;
	}

	public boolean shouldDoGameTimeCycle()
	{
		return doGameTimeCycle;
	}
	public long getSeed() {
		return seed;
	}

	public String getName() {
		return name;
	}
	
	public abstract void drop(ItemStack stack, Vector3d pos, Vector3f dir, float velocity);
	
	public abstract void updateBlock(int x, int y, int z, int block);

	public abstract void update();

	public abstract void queueChunks(ChunkData[] chunks);
	
	public abstract NormalChunk getChunk(int wx, int wy, int wz);

	public final int getBlock(int x, int y, int z) {
		NormalChunk ch = getChunk(x, y, z);
		if (ch != null && ch.isGenerated()) {
			return ch.getBlock(x & Chunk.chunkMask, y & Chunk.chunkMask, z & Chunk.chunkMask);
		} else {
			return 0;
		}
	}



	public abstract void cleanup();

	public abstract CurrentWorldRegistries getCurrentRegistries();
}
