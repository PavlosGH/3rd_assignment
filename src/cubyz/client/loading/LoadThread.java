package cubyz.client.loading;

import java.util.ArrayList;

import cubyz.Constants;
import cubyz.client.*;
import cubyz.utils.Logger;
import cubyz.api.CubyzRegistries;
import cubyz.api.Resource;
import cubyz.api.Side;
import cubyz.gui.menu.LoadingGUI;
import cubyz.modding.ModLoader;
import cubyz.rendering.Mesh;
import cubyz.rendering.ModelLoader;
import cubyz.world.blocks.Blocks;
import cubyz.world.entity.EntityType;

/**
 * Loads all mods.
 */

public class LoadThread extends Thread {

	static int i = -1;
	static Runnable run;
	static ArrayList<Runnable> runnables = new ArrayList<>();
	
	public static void addOnLoadFinished(Runnable run) {
		runnables.add(run);
	}
	
	public void run() {
		setName("Load-Thread");
		Cubyz.renderDeque.add(ClientSettings::load); // run in render thread due to some graphical reasons
		LoadingGUI l = LoadingGUI.getInstance();
		
		l.setStep(1, 0, 0); // load mods
		Constants.setGameSide(Side.CLIENT);
		ModLoader.load();
		
		Object lock = new Object();
		run = () -> {
			i++;
			boolean finishedMeshes = false;
			if (i < CubyzRegistries.ENTITY_REGISTRY.size()) {
				if (i < CubyzRegistries.ENTITY_REGISTRY.size()) {
					EntityType e = CubyzRegistries.ENTITY_REGISTRY.registered(new EntityType[0])[i];
					Meshes.createEntityMesh(e);
				}
				if (i < Blocks.size()-1 || i < CubyzRegistries.ENTITY_REGISTRY.size()-1) {
					Cubyz.renderDeque.add(run);
					l.setStep(2, i+1, Blocks.size());
				} else {
					finishedMeshes = true;
				}
			} else {
				finishedMeshes = true;
			}
			if (finishedMeshes) {
				try {
					Resource res = new Resource("cubyz:sky_body.obj");
					String path = "assets/" + res.getMod() + "/models/3d/" + res.getID();
					GameLauncher.logic.skyBodyMesh = new Mesh(ModelLoader.loadModel(res, path));
				} catch (Exception e) {
					Logger.warning(e);
				}
				synchronized (lock) {
					lock.notifyAll();
				}
			}
		};
		Cubyz.renderDeque.add(run);
		try {
			synchronized (lock) {
				lock.wait();
			}
		} catch (InterruptedException e) {
			return;
		}
		l.setStep(3, 0, 0);

		l.finishLoading();
		
		for (Runnable r : runnables) {
			r.run();
		}
		
		System.gc();
	}
	
}
