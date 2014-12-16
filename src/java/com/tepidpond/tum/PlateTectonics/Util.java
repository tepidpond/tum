package com.tepidpond.tum.PlateTectonics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class Util {
	public static final int getTile(int x, int y, int mapSize) {
		// heightmap is a torus
		return ((y % mapSize) * mapSize + (x % mapSize));
	}
	public static final int getX(int mapTile, int mapSize) {
		return (mapTile % mapSize);
	}
	public static final int getY(int mapTile, int mapSize) {
		return mapTile / mapSize;
	}
	
	public static final void saveHeightmap(float heightMap[], int mapSize, String tag) {
		float hm[] = normalizeHeightMapCopy(heightMap);
		BufferedImage bi = new BufferedImage(mapSize, mapSize, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		for (int x=0; x<mapSize; x++) {
			for (int y=0; y<mapSize; y++) {
				float h = hm[getTile(x, y, mapSize)];
				g.setColor(new Color(h, h, h));
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
