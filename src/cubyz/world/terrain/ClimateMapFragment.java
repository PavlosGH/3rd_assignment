package cubyz.world.terrain;

public class ClimateMapFragment {
	private int MAP_SHIFT = 8 + MapFragment.BIOME_SHIFT;
	private final int MAP_SIZE = 1 << MAP_SHIFT;
	private final int MAP_MASK = MAP_SIZE - 1;
	private final int wx, wz;
	private final BiomePoint[][] map;
	
	public ClimateMapFragment(int wx, int wz) {
		this.wx = wx;
		this.wz = wz;
		map = new BiomePoint[MAP_SIZE/MapFragment.BIOME_SIZE][MAP_SIZE/MapFragment.BIOME_SIZE];
	}
	
	public int getMAP_SHIFT() {
		return MAP_SHIFT;
	}

	public void setMAP_SHIFT(int mAP_SHIFT) {
		MAP_SHIFT = mAP_SHIFT;
	}

	public int getMapSize() {
		return MAP_SIZE;
	}

	public int getMapMask() {
		return MAP_MASK;
	}

	public int getWx() {
		return wx;
	}

	public int getWz() {
		return wz;
	}

	public BiomePoint[][] getMap() {
		return map;
	}

	@Override
	public int hashCode() {
		return hashCode(wx, wz);
	}
	
	public int hashCode(int wx, int wz) {
		return (wx >> MAP_SHIFT)*31 + (wz >> MAP_SHIFT);
	}
}
