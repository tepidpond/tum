package com.tepidpond.tum.PlateTectonics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;

public class Util {
	public static class ImageViewer {
		private static final int maxInstances = 32;
		private static ImageViewer _instances[] = new ImageViewer[maxInstances];
		private JFrame frame;
		private JLabel label;
		
		private ImageViewer() { this(0); }		
		private ImageViewer(int index) {
			if (index < 0 || index >= maxInstances)
				throw new IllegalArgumentException(String.format("You only get %d instances of the viewer.", maxInstances));
			_instances[index] = this;
			if (index == 0)
				frame = new JFrame(String.format("Image Viewer %d", index));
			else 
				frame = new JFrame("Image Viewer");
			label = new JLabel();
			frame.getContentPane().add(label, BorderLayout.CENTER);
			frame.pack();
			if (index == 0) {
				frame.setLocationRelativeTo(null);
				frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			} else {
				frame.setLocationRelativeTo(_instances[index - 1].frame);
				frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
			}
			Hide();
		}
		
		public static void DisplayImage(BufferedImage bi) { DisplayImage(bi, 0); }
		public static void DisplayImage(BufferedImage bi, int index) {
			if (_instances[index] == null) _instances[index] = new ImageViewer(index);
			_instances[index].label.setIcon(new ImageIcon(bi));
			
			Insets ins = _instances[index].frame.getInsets();
			
			_instances[index].frame.setSize(
					bi.getWidth() + ins.left + ins.right,
					bi.getHeight() + ins.bottom + ins.top);
				
			Show(index);
		}

		public static void SetCaption(String caption) { SetCaption(caption, 0); }
		public static void SetCaption(String caption, int index) {
			if (_instances[index] == null) _instances[index] = new ImageViewer(index);
			_instances[index].frame.setTitle(String.format("Image Viewer %d - ", index) + caption);
		}
		
		public static void Hide() { Hide(0); }
		public static void Hide(int index) {
			if (_instances[index] == null) _instances[index] = new ImageViewer(index);
			_instances[index].frame.setVisible(false);
		}
		
		public static void Show() { Show(0); }
		public static void Show(int index) {
			if (_instances[index] == null) _instances[index] = new ImageViewer(index);
			if (!_instances[index].frame.isVisible())
				_instances[index].frame.setVisible(true);
		}
	}
	
	// TODO: Derived from Google search. May be horribly wrong.
	public static final float FLT_EPSILON = 1.19209290e-07f;
	
	public static final int getTile(int x, int y, int mapSize) {
		// heightmap is a torus
		return getTile(x, y, mapSize, mapSize);
	}
	public static final int getTile(int x, int y, int mapWidth, int mapHeight) {
		// heightmap is a torus
		return ((y % mapHeight) * mapWidth + (x % mapWidth));
	}
	public static final int getX(int mapTile, int mapWidth) {
		return (mapTile % mapWidth);
	}
	public static final int getY(int mapTile, int mapWidth) {
		return mapTile / mapWidth;
	}
	public static float quadInterpolate(float[] map, int mapSideLength, float X, float Y) {
		
		// round to nearest int
		int mapOriginX = (int)Math.round(X);
		int mapOriginY = (int)Math.round(Y);
		// save fractional part for interpolating
		X -= mapOriginX; Y -= mapOriginY;
		// wrap into range (0,511) 
		mapOriginX %= mapSideLength; if (mapOriginX < 0) mapOriginX += mapSideLength;
		mapOriginY %= mapSideLength; if (mapOriginY < 0) mapOriginY += mapSideLength;

		// select x/y coordinates for points a(x0, y0), b(x1, y0), c(x0, y1), d(x1, y1)
		// Wrapping at edges is most natural.
		int x0 = mapOriginX, y0 = mapOriginY, x1, y1;
		
		if (X > 0) {
			x1 = (mapOriginX + 1) % mapSideLength;
		} else {
			x1 = mapOriginX == 0 ? mapSideLength - 1: mapOriginX - 1;
			X = -X;
		}
		if (Y > 0) {
			y1 = (mapOriginY + 1) % mapSideLength;
		} else {
			y1 = mapOriginY == 0 ? mapSideLength - 1: mapOriginY - 1;
			Y = -Y;
		}

		// Calc indices for the points in the heightMap.
		float a = map[Util.getTile(x0, y0, mapSideLength)];
		float b = map[Util.getTile(x1, y0, mapSideLength)];
		float c = map[Util.getTile(x0, y1, mapSideLength)];
		float d = map[Util.getTile(x1, y1, mapSideLength)];			

		assert (x0 >= 0 && x0 < mapSideLength && x1 >= 0 && x1 < mapSideLength && x0 >= 0 && y0 < mapSideLength):
	           "Impossibilities in quadInterpolate, failed validation.";

		// quadratic interpolation: a + (b-a)x + (c-a)y + (a-b-c+d)xy
		return a + (b - a) * X + (c - a) * Y + (a + d - (b + c)) * X * Y;
	}
	
	public static BufferedImage renderIntmap(int intMap[], int mapWidth, int mapHeight) {
		BufferedImage bi = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		int mapMin = Integer.MAX_VALUE;
		int mapMax = 0;
		for(int i = 0; i < intMap.length; i++) {
			if (mapMin > intMap[i]) mapMin = intMap[i];
			if (mapMax < intMap[i]) mapMax = intMap[i];
		}
		if (mapMin == mapMax || mapMax == Integer.MAX_VALUE || mapMin == Integer.MAX_VALUE)
			return bi;
		for (int x=0; x<mapWidth; x++) {
			for (int y=0; y<mapHeight; y++) {
				float h = intMap[getTile(x, y, mapWidth, mapHeight)];
				h -= mapMin;
				h /= (mapMax - mapMin);
				
				g.setColor(new Color(h, h, h));
				g.drawLine(x, y, x, y);
			}
		}
		return bi;
	}
	public static BufferedImage renderHeightmap(float heightMap[], int mapWidth, int mapHeight) {
		float hm[] = heightMap; //normalizeHeightMapCopy(heightMap);
		BufferedImage bi = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		float COLOR_STEP = 1.5f;
		for (int x=0; x<mapWidth; x++) {
			for (int y=0; y<mapHeight; y++) {
				float h = hm[getTile(x, y, mapWidth, mapHeight)];
				if (h < 2 * FLT_EPSILON)
					g.setColor(new Color(1, 0, 0));
				else if (h < 0.5)
					g.setColor(new Color(0.0f, 0.0f, 0.25f + 1.5f * h));
				else if (h < 1.0)
					g.setColor(new Color(0, 2 * (h - 0.5f), 1.0f));
				else {
					h -= 1.0;
					if (h < COLOR_STEP)
						g.setColor(new Color(0f, 0.5f + 0.5f * h / COLOR_STEP, 0f));
					else if (h < 1.5 * COLOR_STEP)
						g.setColor(new Color(2f * (h - COLOR_STEP) / COLOR_STEP, 1.0f, 0));
					else if (h < 2.0 * COLOR_STEP)
						g.setColor(new Color(1.0f, 1.0f - (h - 1.5f * COLOR_STEP) / COLOR_STEP, 0));
					else if (h < 3.0 * COLOR_STEP)
						g.setColor(new Color(1.0f - 0.5f * (h - 2.0f * COLOR_STEP) / COLOR_STEP, 0.5f - 0.25f * (h - 2.0f * COLOR_STEP) / COLOR_STEP, 0));
					else if (h < 5.0 * COLOR_STEP)
						g.setColor(new Color(
								0.5f - 0.125f * (h - 3.0f * COLOR_STEP) / (2f * COLOR_STEP),
								0.25f + 0.125f * (h - 3.0f * COLOR_STEP) / (2f * COLOR_STEP),
								0.375f * (h - 3.0f * COLOR_STEP) / (2f * COLOR_STEP)));
					else if (h < 8.0 * COLOR_STEP)
						g.setColor(new Color(
								0.375f + 0.625f * (h - 5.0f * COLOR_STEP) / (3f * COLOR_STEP),
								0.375f + 0.625f * (h - 5.0f * COLOR_STEP) / (3f * COLOR_STEP),
								0.375f + 0.625f * (h - 5.0f * COLOR_STEP) / (3f * COLOR_STEP)));
					else
						g.setColor(new Color(1, 1, 1));
				}
				g.drawLine(x, y, x, y);
			}
		}
		return bi;
	}
	
	public static final void displayHeightmap(float heightMap[], int mapWidth, String tag) {
		displayImage(renderHeightmap(heightMap, mapWidth, mapWidth), tag);
	}
	public static final void displayHeightmap(int index, float heightMap[], int mapWidth, int mapHeight, String tag) {
		displayImage(renderHeightmap(heightMap, mapWidth, mapHeight), index, tag);
	}
	public static final void displayIntmap(int Intmap[], int mapWidth, String tag) {
		displayImage(renderIntmap(Intmap, mapWidth, mapWidth), tag);
	}
	public static final void displayIntmap(int Intmap[], int mapWidth, int mapHeight, String tag) {
		displayImage(renderIntmap(Intmap, mapWidth, mapHeight), tag);
	}
	public static final void saveHeightmap(float heightMap[], int mapWidth, String tag) {
		saveImage(renderHeightmap(heightMap, mapWidth, mapWidth), tag);
	}
	public static final void saveHeightmap(float heightMap[], int mapWidth, int mapHeight, String tag) {
		saveImage(renderHeightmap(heightMap, mapWidth, mapHeight), tag);
	}
	public static final void saveIntmap(int Intmap[], int mapWidth, String tag) {
		saveImage(renderIntmap(Intmap, mapWidth, mapWidth), tag);
	}
	public static final void saveIntmap(int Intmap[], int mapWidth, int mapHeight, String tag) {
		saveImage(renderIntmap(Intmap, mapWidth, mapHeight), tag);
	}
	public static final void displayImage(BufferedImage bi, String tag) { displayImage(bi, 0, tag); }
	public static final void displayImage(BufferedImage bi, int index, String tag) {
		ImageViewer.DisplayImage(bi, index);
		ImageViewer.SetCaption(tag, index);
	}
	public static final void saveImage(BufferedImage bi, String tag) {
		try {
			File o = new File("HM" + Long.toString(System.currentTimeMillis()) + "." + tag + ".png");
			ImageIO.write(bi, "PNG", o);
		} catch (Exception e) {
			System.out.println("Error saving heightmap.png because: " + e.getMessage());
		}
	}
	// force values in the heightMap into a standard [0.0f ... 1.0f] range.
	public static void normalizeHeightMap(float heightMap[]) {
		int mapArea = heightMap.length;
		float minHeight = heightMap[0], maxHeight = heightMap[0];
		for (int i = 1; i < mapArea; i++) {
			if (heightMap[i] < minHeight) minHeight = heightMap[i];
			if (heightMap[i] > maxHeight) maxHeight = heightMap[i];
		}
		
		float scaleFactor = maxHeight - minHeight;
		//if (min != 0.0f) minHeight -= min;
		//if (min != 0.0f || max != 1.0f) scaleFactor /= (max - min);

		for (int i = 0; i < mapArea; i++) {
			heightMap[i] = (heightMap[i] - minHeight) / scaleFactor;
		}
	}
	
	// force values in the heightMap into a standard [0.0f ... 1.0f] range.
	public static float[] normalizeHeightMapCopy(float heightMap[]) {
		float tmpHM[] = new float[heightMap.length];
		System.arraycopy(heightMap, 0, tmpHM, 0, heightMap.length);
		normalizeHeightMap(tmpHM);
		return tmpHM;
	}
}
