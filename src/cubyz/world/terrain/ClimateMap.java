package cubyz.world.terrain;

import cubyz.utils.datastructures.Cache;

public final class ClimateMap {
	private ClimateMap() {} // No instances allowed.

	private static final int CACHE_SIZE = 1 << 8; // Must be a power of 2!
	private static final int CACHE_MASK = CACHE_SIZE - 1;
	private static final int ASSOCIATIVITY = 4;
	private static final Cache<ClimateMapFragment> cache = new Cache<>(new ClimateMapFragment[CACHE_SIZE][ASSOCIATIVITY]);
	private static ClimateMapFragment climateMap = new ClimateMapFragment(0, 0);
	

	private static TerrainGenerationProfile profile;

	public static BiomePoint[][] getBiomeMap(int wx, int wz, int width, int height) {
		BiomePoint[][] map = new BiomePoint[width/MapFragment.BIOME_SIZE][height/MapFragment.BIOME_SIZE];
		int wxStart = wx & ~climateMap.getMapMask();
		int wzStart = wz & ~climateMap.getMapMask();
		int wxEnd = wx+width & ~climateMap.getMapMask();
		int wzEnd = wz+height & ~climateMap.getMapMask();
		for(int x = wxStart; x <= wxEnd; x += climateMap.getMapSize()) {
			for(int z = wzStart; z <= wzEnd; z += climateMap.getMapSize()) {
				ClimateMapFragment mapPiece = getOrGenerateFragment(x, z);
				// Offset of the indices in the result map:
				int xOffset = (x - wx) >> MapFragment.BIOME_SHIFT;
				int zOffset = (z - wz) >> MapFragment.BIOME_SHIFT;
				// Go through all indices in the mapPiece:
				for(int lx = 0; lx < mapPiece.getMap().length; lx++) {
					int resultX = lx + xOffset;
					if (resultX < 0 || resultX >= map.length) continue;
					for(int lz = 0; lz < mapPiece.getMap()[0].length; lz++) {
						int resultZ = lz + zOffset;
						if (resultZ < 0 || resultZ >= map.length) continue;
						map[resultX][resultZ] = mapPiece.getMap()[lx][lz];
					}
				}
			}
		}
		return map;
	}
	
	private static final class ClimateMapFragmentComparator {
		private final int wx, wz;
		private ClimateMapFragmentComparator(int wx, int wz) {
			this.wx = wx;
			this.wz = wz;
		}
		@Override
		public boolean equals(Object other) {
			if (other instanceof ClimateMapFragment) {
				return ((ClimateMapFragment)other).getWx() == wx && ((ClimateMapFragment)other).getWz() == wz;
			}
			return false;
		}
	}
	
	public static ClimateMapFragment getOrGenerateFragment(int wx, int wz) {
		int hash = climateMap.hashCode(wx, wz) & CACHE_MASK;
		ClimateMapFragment ret = cache.find(new ClimateMapFragmentComparator(wx, wz), hash);
		if (ret != null) return ret;
		synchronized(cache.cache[hash]) {
			// Try again in case it was already generated in another thread:
			ret = cache.find(new ClimateMapFragmentComparator(wx, wz), hash);
			if (ret != null) return ret;
			ret = new ClimateMapFragment(wx, wz);
			profile.climateGenerator.generateMapFragment(ret, profile.seed);
			cache.addToCache(ret, ret.hashCode() & CACHE_MASK);
			return ret;
		}
	}

	public static void cleanup() {
		cache.clear();
	}

	public static void init(TerrainGenerationProfile profile) {
		ClimateMap.profile = profile;
	}
}
