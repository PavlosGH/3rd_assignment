package cubyz.client.entity;

import cubyz.Constants;
import cubyz.utils.interpolation.GenericInterpolation;
import cubyz.utils.interpolation.TimeDifference;
import cubyz.world.World;
import cubyz.world.entity.ItemEntityManager;
import cubyz.world.items.ItemStack;

public class InterpolatedItemEntityManager extends ItemEntityManager {
	public final GenericInterpolation interpolation = new GenericInterpolation(super.posxyz, super.velxyz);
	private short lastTime = (short)System.currentTimeMillis();
	public final TimeDifference timeDifference = new TimeDifference();

	public InterpolatedItemEntityManager(World world) {
		super(world);
	}

	@Override
	public void update(float deltaTime) {
		throw new IllegalArgumentException();
	}

	public void updateInterpolationData() {
		short time = (short)(System.currentTimeMillis() - Constants.ENTITY_LOOKBACK);
		time -= timeDifference.difference;
		interpolation.updateIndexed(time, lastTime, indices, size, 3);
		lastTime = time;
	}

	@Override
	public void add(int i, double x, double y, double z, double vx, double vy, double vz, float rotX, float rotY, float rotZ, ItemStack itemStack, int despawnTime, int pickupCooldown) {
		synchronized(this) {
			for(int j = 0; j < interpolation.lastVelocity.length; j++) {
				interpolation.lastVelocity[j][3*i] = 0;
				interpolation.lastVelocity[j][3*i + 1] = 0;
				interpolation.lastVelocity[j][3*i + 2] = 0;
				interpolation.lastPosition[j][3*i] = x;
				interpolation.lastPosition[j][3*i + 1] = y;
				interpolation.lastPosition[j][3*i + 2] = z;
			}
			super.add(i, x, y, z, vx, vy, vz, rotX, rotY, rotZ, itemStack, despawnTime, pickupCooldown);
		}
	}
}
