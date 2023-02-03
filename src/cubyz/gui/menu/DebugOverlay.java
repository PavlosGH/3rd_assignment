package cubyz.gui.menu;

import cubyz.Constants;
import cubyz.client.ClientSettings;
import cubyz.client.Cubyz;
import cubyz.client.GameLauncher;
import cubyz.client.GameLogic;
import cubyz.gui.MenuGUI;
import cubyz.multiplayer.Protocols;
import cubyz.multiplayer.UDPConnection;
import cubyz.rendering.Graphics;
import cubyz.rendering.SSBO;
import cubyz.rendering.ShaderProgram;
import cubyz.rendering.Window;
import cubyz.rendering.text.Fonts;
import cubyz.utils.Logger;
import cubyz.utils.ThreadPool;
import cubyz.utils.Utils;
import cubyz.world.entity.Player;

import static org.lwjgl.opengl.GL43.*;

import java.io.IOException;

import static cubyz.client.ClientSettings.GUI_SCALE;

/**
 * This is the F3 debug menu
 * @author zenith391
 */

public class DebugOverlay extends MenuGUI {

	String javaVersion = System.getProperty("java.version");
	
	private static final float[] lastFrameTime = new float[2048];
	private static int index = 0;
	private static final SSBO graphBuffer = new SSBO();
	private static ShaderProgram graphShader;

	public static final class GraphUniforms {
		public static int loc_start;
		public static int loc_dimension;
		public static int loc_screen;
		public static int loc_points;
		public static int loc_offset;
		public static int loc_lineColor;
	}

	static {
		try {
			graphShader = new ShaderProgram(Utils.loadResource("assets/cubyz/shaders/graphics/graph.vs"),
					Utils.loadResource("assets/cubyz/shaders/graphics/graph.fs"), GraphUniforms.class);
		} catch (IOException e) {
			Logger.error(e);
		}
	}
	
	public static void addFrameTime(float deltaTime) {
		lastFrameTime[index] = deltaTime;
		index = (index + 1)%lastFrameTime.length;
	}
	
	@Override
	public void render() {
		if (GameLauncher.input.clientShowDebug) {
			Graphics.setFont(Fonts.PIXEL_FONT, 8.0F * GUI_SCALE);
			Graphics.setColor(0xFFFFFF);
			Graphics.drawText(0 * GUI_SCALE, 0 * GUI_SCALE, GameLogic.getFPS() + " fps" + (Window.isVSyncEnabled() ? " (vsync)" : ""));
			//TODO: tick speed
			Graphics.drawText(0 * GUI_SCALE, 10 * GUI_SCALE, "Branded \"" + Constants.GAME_BRAND + "\", version " + Constants.GAME_VERSION);
			Graphics.drawText(0 * GUI_SCALE, 20 * GUI_SCALE, "Windowed (" + Window.getWidth() + "x" + Window.getHeight() + ")");
			Graphics.drawText(0 * GUI_SCALE, 30 * GUI_SCALE, "Java " + javaVersion);
			long totalMemory = Runtime.getRuntime().totalMemory()/1024/1024;
			long freeMemory = Runtime.getRuntime().freeMemory()/1024/1024;
			long maxMemory = Runtime.getRuntime().maxMemory()/1024/1024;
			Graphics.drawText(0 * GUI_SCALE, 90 * GUI_SCALE, "Memory: " + (totalMemory - freeMemory) + "/" + totalMemory + "MiB (max " + maxMemory + "MiB)");
			
			if (Cubyz.world != null) {
				Player p = Cubyz.player;
				double x = p.getPosition().x;
				double y = p.getPosition().y;
				double z = p.getPosition().z;
				
				Graphics.drawText(0 * GUI_SCALE, 40 * GUI_SCALE, "XYZ: " + Math.round(100*x)/100.0 + ", " + Math.round(100*y)/100.0 + ", " + Math.round(100*z)/100.0);
				Graphics.drawText(0 * GUI_SCALE, 50 * GUI_SCALE, "Render Distance: " + ClientSettings.RENDER_DISTANCE);
				Graphics.drawText(0 * GUI_SCALE, 60 * GUI_SCALE, "Game Time: " + Cubyz.world.gameTime);
				Graphics.drawText(0*GUI_SCALE, 70*GUI_SCALE, "Queue Size: " + ThreadPool.getQueueSize());
				Graphics.drawText(0 * GUI_SCALE, 80 * GUI_SCALE, "Biome: " + (Cubyz.world.playerBiome == null ? "null" : Cubyz.world.playerBiome.getRegistryID()));

				Graphics.drawText(0*GUI_SCALE, 100*GUI_SCALE, "Packet loss: "+Math.round(10000*UDPConnection.packets_resent/(float)UDPConnection.packets_sent)/100.0f+"% ("+UDPConnection.packets_resent+"/"+ UDPConnection.packets_sent +")");
				Graphics.drawText(0*GUI_SCALE, 110*GUI_SCALE, "Important Protocols total: " + (Protocols.bytesReceived[Protocols.IMPORTANT_PACKET & 0xff] >> 10) + "kiB in " + Protocols.packetsReceived[Protocols.IMPORTANT_PACKET & 0xff] + " packets");
				Graphics.drawText(0*GUI_SCALE, 120*GUI_SCALE, "Keep-alive: " + (Protocols.bytesReceived[Protocols.KEEP_ALIVE] >> 10) + "kiB in " + Protocols.packetsReceived[Protocols.KEEP_ALIVE] + " packets");
				int yText = 130;
				for(int i = 0; i < Protocols.bytesReceived.length; i++) {
					if(Protocols.list[i] != null) {
						Graphics.drawText(0*GUI_SCALE, yText*GUI_SCALE, Protocols.list[i].getClass().getSimpleName() + ": " + (Protocols.bytesReceived[i] >> 10) + "kiB in " + Protocols.packetsReceived[i] + " packets");
						yText += 10;
					}
				}
			}
			
			int h = Window.getHeight();
			Graphics.drawText(0 * GUI_SCALE, h - 10*GUI_SCALE, "00 ms");
			Graphics.drawText(0 * GUI_SCALE, h - 26*GUI_SCALE, "16 ms");
			Graphics.drawText(0 * GUI_SCALE, h - 42*GUI_SCALE, "32 ms");
			Graphics.setColor(0xffffff, 128);
			int xOffset = 20*GUI_SCALE;
			Graphics.drawLine(xOffset, h - 4*GUI_SCALE, Window.getWidth()/2, h - 4*GUI_SCALE);
			Graphics.drawLine(xOffset, h - 20*GUI_SCALE, Window.getWidth()/2, h - 20*GUI_SCALE);
			Graphics.drawLine(xOffset, h - 36*GUI_SCALE, Window.getWidth()/2, h - 36*GUI_SCALE);
			graphShader.bind();
			glUniform2f(GraphUniforms.loc_start, xOffset, Window.getHeight() - 4*GUI_SCALE);
			glUniform2f(GraphUniforms.loc_dimension, Window.getWidth()/2 - xOffset, GUI_SCALE);
			glUniform2f(GraphUniforms.loc_screen, Window.getWidth(), Window.getHeight());
			glUniform1i(GraphUniforms.loc_points, lastFrameTime.length);
			glUniform1i(GraphUniforms.loc_offset, index);
			glUniform3f(GraphUniforms.loc_lineColor, 1, 1, 1);
			graphBuffer.bufferData(lastFrameTime);
			graphBuffer.bind(4);
			glDrawArrays(GL_LINE_STRIP, 0, lastFrameTime.length);
		}
	}

	@Override
	public boolean doesPauseGame() {
		return false;
	}

	@Override
	public void init() {}

	@Override
	public void updateGUIScale() {}

}