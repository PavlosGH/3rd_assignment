package cubyz.world.terrain.worldgenerators;

import cubyz.api.CurrentWorldRegistries;
import cubyz.api.Resource;
import cubyz.world.terrain.BiomePoint;
import cubyz.world.terrain.ClimateMapFragment;
import cubyz.world.terrain.ClimateMapGenerator;
import cubyz.world.terrain.MapFragment;
import cubyz.world.terrain.biomes.Biome;
import pixelguys.json.JsonObject;

/**
 * Generates a flat land filled with grass, dirt and stone.
 */
public class FlatLand implements ClimateMapGenerator {
	
	private Biome FLATLAND;

	@Override
	public void init(JsonObject parameters, CurrentWorldRegistries registries) {
		FLATLAND = registries.biomeRegistry.getByID("cubyz:flatland");
	}

	@Override
	public Resource getRegistryID() {
		return new Resource("cubyz:flatland");
	}

	@Override
	public void generateMapFragment(ClimateMapFragment map, long seed) {
		for (int x = 0; x < map.getMap().length; x++) {
			for (int z = 0; z < map.getMap()[0].length; z++) {
				map.getMap()[x][z] = new BiomePoint(FLATLAND, map.getWx() + x*MapFragment.BIOME_SIZE, map.getWz() + z*MapFragment.BIOME_SIZE, 32, 0);
			}
		}
	}
}
