package com.tepidpond.tum.PlateTectonics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class Util {
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
	
	public static final void saveHeightmap(float heightMap[], int mapWidth, String tag) {
		saveHeightmap(heightMap, mapWidth, mapWidth, tag);
	}
	public static final void saveHeightmap(float heightMap[], int mapWidth, int mapHeight, String tag) {
		float hm[] = heightMap; //normalizeHeightMapCopy(heightMap);
		BufferedImage bi = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		for (int x=0; x<mapWidth; x++) {
			for (int y=0; y<mapHeight; y++) {
				float h = hm[getTile(x, y, mapWidth, mapHeight)];
				g.setColor(new Color(Color.HSBtoRGB(1.0f, 0.0f, h)));
				g.drawLine(x, y, x, y);
			}
		}
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
