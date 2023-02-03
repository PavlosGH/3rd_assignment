package cubyz.gui.input;

import cubyz.utils.Logger;
import cubyz.utils.datastructures.IntSimpleList;

import static org.lwjgl.glfw.GLFW.*;

public final class Keyboard {
	private Keyboard() {} // No instances allowed.

	static IntSimpleList pressedKeys = new IntSimpleList();
	static IntSimpleList releasedKeys = new IntSimpleList();
	private static final int BUFFER_LEN = 256;
	static final char[] charBuffer = new char[BUFFER_LEN]; // Pseudo-circular buffer of the last chars, to avoid problems if the user is a fast typer or uses macros or compose key.
	private static int lastStart = 0, lastEnd = 0, current = 0;
	static int keyMods;
	
	/**
	 * There can be only one KeyListener to prevent issues like interacting with multiple GUI elements at the same time.
	 */
	public static KeyListener activeComponent;
	
	public static void pushChar(char ch) {
		int next = (current+1)%BUFFER_LEN;
		if (next == lastStart) {
			Logger.warning("Char buffer is full. Ignoring char '"+ch+"'.");
			return;
		}
		charBuffer[current] = ch;
		current = next;
	}
	
	public static boolean hasCharSequence() {
		return lastStart != lastEnd;
	}

	public static void glfwKeyCallback(int key, int scancode, int action, int mods) {
		setKeyPressed(key, action != GLFW_RELEASE);
		setKeyMods(mods);
		if(action == GLFW_RELEASE) {
			releasedKeys.add(key);
		}
	}
	
	/**
	 * Returns the last chars input by the user.
	 * @return chars typed in by the user. Calls to backspace are encrypted using '\0'.
	 */
	public static char[] getCharSequence() {
		char[] sequence = new char[(lastEnd - lastStart + BUFFER_LEN)%BUFFER_LEN];
		int index = 0;
		for(int i = lastStart; i != lastEnd; i = (i+1)%BUFFER_LEN) {
			sequence[index++] = charBuffer[i];
		}
		return sequence;
	}
	
	/**
	/**
	 * Resets buffers.
	 */
	public static void release() {
		lastStart = lastEnd;
		lastEnd = current;
		releasedKeys.clear();
	}
	
	public static boolean isKeyPressed(int key) {
		return pressedKeys.contains(key);
	}
	
	public static boolean isKeyReleased(int key) {
		return releasedKeys.contains(key);
	}
	
	/**
	 * Key mods are additional control key pressed with the current key. (e.g. C is pressed with Shift+Ctrl)
	 * @return key mods
	 */
	public static int getKeyMods() {
		return keyMods;
	}
	
	public static void setKeyMods(int mods) {
		keyMods = mods;
	}
	
	public static void setKeyPressed(int key, boolean press) {
		if (press) {
			if (activeComponent != null)
				activeComponent.onKeyPress(key);
			if (!pressedKeys.contains(key)) {
				pressedKeys.add(key);
			}
		} else {
			if (pressedKeys.contains(key)) {
				pressedKeys.remove(key);
			}
		}
	}

}
