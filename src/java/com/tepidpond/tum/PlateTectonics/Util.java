package com.tepidpond.tum.PlateTectonics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;

public class Util {
	public static class ImageViewer {
		private static ImageViewer _instance = new ImageViewer();
		private JFrame frame;
		private JLabel label;
		private ImageViewer() {
			_instance = this;
			frame = new JFrame("Image Viewer");
			label = new JLabel();
			frame.getContentPane().add(label, BorderLayout.CENTER);
			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			Hide();
		}
		public static void DisplayImage(BufferedImage bi) {
			_instance.label.setIcon(new ImageIcon(bi));
			_instance.frame.setSize(bi.getWidth(), bi.getHeight());
			_instance.frame.setLocation(20, 20);
			Show();
		}
		public static void SetCaption(String caption) {
			_instance.frame.setTitle(caption);
		}
		public static void Hide() {
			_instance.frame.setVisible(false);
		}
		public static void Show() {
			_instance.frame.setVisible(true);
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
	public static final void displayHeightmap(float heightMap[], int mapWidth, int mapHeight, String tag) {
		displayImage(renderHeightmap(heightMap, mapWidth, mapHeight), tag);
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
	public static final void displayImage(BufferedImage bi, String tag) {
		ImageViewer.DisplayImage(bi);
		ImageViewer.SetCaption(tag);
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
