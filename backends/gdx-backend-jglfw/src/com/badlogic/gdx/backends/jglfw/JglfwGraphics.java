
package com.badlogic.gdx.backends.jglfw;

import static com.badlogic.jglfw.Glfw.*;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.GL11;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GLCommon;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.jglfw.GlfwVideoMode;
import com.badlogic.jglfw.gl.GL;

import java.awt.Toolkit;

/** An implementation of the {@link Graphics} interface based on GLFW.
 * @author Nathan Sweet */
public class JglfwGraphics implements Graphics {
	static int glMajorVersion, glMinorVersion;

	long window;
	private boolean fullscreen;
	private long fullscreenMonitor;
	private String title;
	private boolean resizable, undecorated;
	private BufferFormat bufferFormat;
	private boolean vSync;
	private int x, y, width, height;
	private boolean visible;
	private Color initialBackgroundColor;
	private volatile boolean isContinuous = true;
	private volatile boolean renderRequested;

	private float deltaTime;
	private long frameStart, lastTime = -1;
	private int frames, fps;

	private GLCommon gl;
	private JglfwGL10 gl10;
	private JglfwGL11 gl11;
	private JglfwGL20 gl20;

	public JglfwGraphics (JglfwApplicationConfiguration config) {
		// Store values from config.
		bufferFormat = new BufferFormat(config.r, config.g, config.b, config.a, config.depth, config.stencil, config.samples, false);
		title = config.title;
		resizable = config.resizable;
		undecorated = config.undecorated;
		x = config.x;
		y = config.y;
		vSync = config.vSync;
		initialBackgroundColor = config.initialBackgroundColor;
		if (config.fullscreenMonitorIndex != -1) { // Use monitor specified in config if it is valid.
			long[] monitors = glfwGetMonitors();
			if (config.fullscreenMonitorIndex < monitors.length) fullscreenMonitor = monitors[config.fullscreenMonitorIndex];
		}

		// Create window.
		if (!createWindow(config.width, config.height, config.fullscreen)) {
			throw new GdxRuntimeException("Unable to create window: " + config.width + "x" + config.height + ", fullscreen: "
				+ config.fullscreen);
		}

		// Create GL.
		String version = GL.glGetString(GL11.GL_VERSION);
		glMajorVersion = Integer.parseInt("" + version.charAt(0));
		glMinorVersion = Integer.parseInt("" + version.charAt(2));
		if (config.useGL20 && (glMajorVersion >= 2 || version.contains("2.1"))) { // special case for MESA, wtf...
			gl20 = new JglfwGL20();
			gl = gl20;
		} else {
			gl20 = null;
			if (glMajorVersion == 1 && glMinorVersion < 5)
				gl10 = new JglfwGL10();
			else {
				gl11 = new JglfwGL11();
				gl10 = gl11;
			}
			gl = gl10;
		}
		Gdx.gl = gl;
		Gdx.gl10 = gl10;
		Gdx.gl11 = gl11;
		Gdx.gl20 = gl20;

		if (!config.hidden) show();
	}

	private boolean createWindow (int width, int height, boolean fullscreen) {
		if (fullscreen && fullscreenMonitor == 0) fullscreenMonitor = getWindowMonitor();

		glfwWindowHint(GLFW_VISIBLE, 0);
		glfwWindowHint(GLFW_RESIZABLE, resizable ? 1 : 0);
		glfwWindowHint(GLFW_UNDECORATED, undecorated ? 1 : 0);
		glfwWindowHint(GLFW_RED_BITS, bufferFormat.r);
		glfwWindowHint(GLFW_GREEN_BITS, bufferFormat.g);
		glfwWindowHint(GLFW_BLUE_BITS, bufferFormat.b);
		glfwWindowHint(GLFW_ALPHA_BITS, bufferFormat.a);
		glfwWindowHint(GLFW_DEPTH_BITS, bufferFormat.depth);
		glfwWindowHint(GLFW_STENCIL_BITS, bufferFormat.stencil);
		glfwWindowHint(GLFW_SAMPLES, bufferFormat.samples);

		boolean mouseCaptured = window != 0 && glfwGetInputMode(window, GLFW_CURSOR_MODE) == GLFW_CURSOR_CAPTURED;

		long oldWindow = window;
		long newWindow = glfwCreateWindow(width, height, title, fullscreen ? fullscreenMonitor : 0, oldWindow);
		if (newWindow == 0) return false;
		if (oldWindow != 0) glfwDestroyWindow(oldWindow);
		window = newWindow;
		this.width = Math.max(1, width);
		this.height = Math.max(1, height);

		this.fullscreen = fullscreen;
		if (!fullscreen) {
			if (x == -1 || y == -1) {
				DisplayMode mode = getDesktopDisplayMode();
				x = (mode.width - width) / 2;
				y = (mode.height - height) / 2;
			}
			glfwSetWindowPos(window, x, y);
		}

		if (!mouseCaptured) glfwSetInputMode(window, GLFW_CURSOR_MODE, GLFW_CURSOR_NORMAL); // Prevent fullscreen from taking mouse.

		glfwMakeContextCurrent(newWindow);
		setVSync(vSync);
		if (visible) glfwShowWindow(window);

		return true;
	}

	void frameStart () {
		long time = System.nanoTime();
		if (lastTime == -1) lastTime = time;
		deltaTime = (time - lastTime) / 1000000000.0f;
		lastTime = time;

		if (time - frameStart >= 1000000000) {
			fps = frames;
			frames = 0;
			frameStart = time;
		}
		frames++;
	}

	void sizeChanged (int width, int height) {
		width = Math.max(1, width);
		height = Math.max(1, height);
		this.width = width;
		this.height = height;
		Gdx.gl.glViewport(0, 0, width, height);
		ApplicationListener listener = Gdx.app.getApplicationListener();
		if (listener != null) listener.resize(width, height);
		requestRendering();
	}

	void positionChanged (int x, int y) {
		this.x = x;
		this.y = y;
	}

	public boolean isGL11Available () {
		return gl11 != null;
	}

	public boolean isGL20Available () {
		return gl20 != null;
	}

	public GLCommon getGLCommon () {
		return gl;
	}

	public GL10 getGL10 () {
		return gl10;
	}

	public GL11 getGL11 () {
		return gl11;
	}

	public GL20 getGL20 () {
		return gl20;
	}

	public int getWidth () {
		return width;
	}

	public int getHeight () {
		return height;
	}

	public float getDeltaTime () {
		return deltaTime;
	}

	public float getRawDeltaTime () {
		return deltaTime;
	}

	public int getFramesPerSecond () {
		return fps;
	}

	public GraphicsType getType () {
		return GraphicsType.JGLFW;
	}

	public float getPpiX () {
		// return getWidth() / (glfwGetMonitorPhysicalWidth(getWindowMonitor()) * 0.03937f); // mm to inches
		return Toolkit.getDefaultToolkit().getScreenResolution();
	}

	public float getPpiY () {
		// return getHeight() / (glfwGetMonitorPhysicalHeight(getWindowMonitor()) * 0.03937f); // mm to inches
		return Toolkit.getDefaultToolkit().getScreenResolution();
	}

	public float getPpcX () {
		// return getWidth() / (glfwGetMonitorPhysicalWidth(getWindowMonitor()) / 10); // mm to cm
		return Toolkit.getDefaultToolkit().getScreenResolution() / 2.54f;
	}

	public float getPpcY () {
		// return getHeight() / (glfwGetMonitorPhysicalHeight(getWindowMonitor()) / 10); // mm to cm
		return Toolkit.getDefaultToolkit().getScreenResolution() / 2.54f;
	}

	public float getDensity () {
		// long monitor = getWindowMonitor();
		// float mmWidth = glfwGetMonitorPhysicalWidth(monitor);
		// float mmHeight = glfwGetMonitorPhysicalHeight(monitor);
		// float inches = (float)Math.sqrt(mmWidth * mmWidth + mmHeight * mmHeight) * 0.03937f; // mm to inches
		// float pixelWidth = getWidth();
		// float pixelHeight = getHeight();
		// float pixels = (float)Math.sqrt(pixelWidth * pixelWidth + pixelHeight * pixelHeight);
		// float diagonalPpi = pixels / inches;
		// return diagonalPpi / 160f;
		return Toolkit.getDefaultToolkit().getScreenResolution() / 160f;
	}

	public boolean supportsDisplayModeChange () {
		return true;
	}

	private long getWindowMonitor () {
		if (window != 0) {
			long monitor = glfwGetWindowMonitor(window);
			if (monitor != 0) return monitor;
		}
		return glfwGetPrimaryMonitor();
	}

	public DisplayMode[] getDisplayModes () {
		Array<DisplayMode> modes = new Array();
		for (GlfwVideoMode mode : glfwGetVideoModes(getWindowMonitor()))
			modes.add(new JglfwDisplayMode(mode.width, mode.height, 0, mode.redBits + mode.greenBits + mode.blueBits));
		return modes.toArray(DisplayMode.class);
	}

	public DisplayMode getDesktopDisplayMode () {
		GlfwVideoMode mode = glfwGetVideoMode(getWindowMonitor());
		return new JglfwDisplayMode(mode.width, mode.height, 0, mode.redBits + mode.greenBits + mode.blueBits);
	}

	public boolean setDisplayMode (DisplayMode displayMode) {
		bufferFormat = new BufferFormat( //
			displayMode.bitsPerPixel == 16 ? 5 : 8, //
			displayMode.bitsPerPixel == 16 ? 6 : 8, //
			displayMode.bitsPerPixel == 16 ? 6 : 8, //
			bufferFormat.a, bufferFormat.depth, bufferFormat.stencil, bufferFormat.samples, false);
		boolean success = createWindow(displayMode.width, displayMode.height, fullscreen);
		if (success && fullscreen) sizeChanged(displayMode.width, displayMode.height);
		return success;
	}

	public boolean setDisplayMode (int width, int height, boolean fullscreen) {
		if (fullscreen || this.fullscreen) {
			boolean success = createWindow(width, height, fullscreen);
			if (success && fullscreen) sizeChanged(width, height);
			return success;
		}

		glfwSetWindowSize(window, width, height);
		return true;
	}

	public void setTitle (String title) {
		if (title == null) title = "";
		glfwSetWindowTitle(window, title);
		this.title = title;
	}

	public void setVSync (boolean vsync) {
		this.vSync = vsync;
		glfwSwapInterval(vsync ? 1 : 0);
	}

	public BufferFormat getBufferFormat () {
		return bufferFormat;
	}

	public boolean supportsExtension (String extension) {
		return glfwExtensionSupported(extension);
	}

	public void setContinuousRendering (boolean isContinuous) {
		this.isContinuous = isContinuous;
	}

	public boolean isContinuousRendering () {
		return isContinuous;
	}

	public void requestRendering () {
		renderRequested = true;
	}

	public boolean isFullscreen () {
		return fullscreen;
	}

	/** Returns the JGLFW window handle. Note this should not be stored externally as it may change if the window is recreated to
	 * enter/exit fullscreen. */
	public long getWindow () {
		return window;
	}

	public int getX () {
		return x;
	}

	public int getY () {
		return y;
	}

	public void setPosition (int x, int y) {
		glfwSetWindowPos(window, x, y);
	}

	public void hide () {
		visible = false;
		glfwHideWindow(window);
	}

	public void show () {
		visible = true;
		glfwShowWindow(window);

		Gdx.gl.glClearColor(initialBackgroundColor.r, initialBackgroundColor.g, initialBackgroundColor.b, initialBackgroundColor.a);
		Gdx.gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
		glfwSwapBuffers(window);
	}

	public boolean isMinimized () {
		return glfwGetWindowParam(window, GLFW_ICONIFIED) == 1;
	}

	public void minimize () {
		glfwIconifyWindow(window);
	}

	public void restore () {
		glfwRestoreWindow(window);
	}

	boolean shouldRender () {
		try {
			return renderRequested || isContinuous;
		} finally {
			renderRequested = false;
		}
	}

	static class JglfwDisplayMode extends DisplayMode {
		protected JglfwDisplayMode (int width, int height, int refreshRate, int bitsPerPixel) {
			super(width, height, refreshRate, bitsPerPixel);
		}
	}
}
