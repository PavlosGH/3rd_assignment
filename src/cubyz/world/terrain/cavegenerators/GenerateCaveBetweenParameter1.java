package cubyz.world.terrain.cavegenerators;

import cubyz.world.terrain.CaveMapFragment;

public class GenerateCaveBetweenParameter1 {
	public long seed;
	public CaveMapFragment map;
	public double startwx;
	public double startwy;
	public double startwz;
	public double endwx;
	public double endwy;
	public double endwz;

	public GenerateCaveBetweenParameter1(long seed, CaveMapFragment map, double startwx, double startwy, double startwz,
			double endwx, double endwy, double endwz) {
		this.seed = seed;
		this.map = map;
		this.startwx = startwx;
		this.startwy = startwy;
		this.startwz = startwz;
		this.endwx = endwx;
		this.endwy = endwy;
		this.endwz = endwz;
	}
}