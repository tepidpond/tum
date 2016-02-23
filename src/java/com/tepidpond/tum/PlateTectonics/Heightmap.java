package com.tepidpond.tum.PlateTectonics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Array;
import java.security.InvalidParameterException;

import javax.imageio.ImageIO;

public class Heightmap<T> {
	public interface HeightmapCallback {
		boolean Process(int X, int Y, Object value);
	}
	private int Xoffset, Yoffset;
	private int mapWidth, mapHeight;
	private int mapArea;
	private Heightmap<T> parent;
	private T storage[];

	/*
	 * Getters and setters
	 */
	public int getX0() {return Xoffset;}
	public int getX1() {return Xoffset + mapWidth - 1;}
	public int getY0() {return Yoffset;}
	public int getY1() {return Yoffset + mapHeight - 1;}
	public int getArea() {return mapWidth * mapHeight;}
	public Heightmap<T> getParent() {return parent;}
	
	public T get(int X, int Y) {
		if (!contains(X, Y)) {
			if (parent != null)
				return (T) parent.get(X, Y);
			else
				throw new InvalidParameterException(String.format("Point (%d,%d) is outside of bounds [%d,%d]-[%d,%d].", X, Y, getX0(), getY0(), getX1(), getY1()));
		}
		return storage[getIndex(X, Y)];
	}

	public void set(int X, int Y,  T value) {
		if (!contains(X, Y)) {
			if (parent != null)
				parent.set(X, Y, value);
			else
				throw new InvalidParameterException(String.format("Point (%d,%d) is outside of bounds [%d,%d]-[%d,%d].", X, Y, getX0(), getY0(), getX1(), getY1()));
		}
		storage[getIndex(X, Y)] = value;
	}
	
	public T getByLocal(int localX, int localY) {
		return get(localX + Xoffset, localY + Yoffset);
	}

	public void setByLocal(int localX, int localY,  T value) {
		set(localX + Xoffset, localY + Yoffset, value);
	}

	public T getByIndex(int index) {
		if (index < 0 || index > mapArea)
			throw new InvalidParameterException(String.format("Index %d outside of map area %d.", index, mapArea));
		return storage[index];
	}
	
	public void setByIndex(int index, T value) {
		if (index < 0 || index > mapArea)
			throw new InvalidParameterException(String.format("Index %d outside of map area %d.", index, mapArea));
		storage[index] = value;
	}
	
	public T[] getRaw() {
		return storage;
	}

	public void setRaw(T[] data) {
		if (data.length != storage.length)
			throw new InvalidParameterException(String.format("Cannot overwrite storage with different sized storage. %d != %d", storage.length, data.length));
			
		System.arraycopy(data, 0, storage, 0, storage.length);
	}
	
	/*
	 * Constructor and book-keeping
	 */
	public Heightmap(Class<T> cls, Heightmap parentMap, int originX, int originY, int width, int height) {
		if (parentMap != null && parentMap.getParent() != null)
			throw new InvalidParameterException("You cannot multiply nest heightmaps.");
		if (parentMap == null && (originX != 0 || originY != 0))
			throw new InvalidParameterException("Origin offset only makes sense with nested heightmaps.");
		if (width < 1 || height < 1)
			throw new InvalidParameterException("Heightmaps must have non-zero area.");

		this.Xoffset = originX;
		this.Yoffset = originY;
		this.mapWidth = width;
		this.mapHeight = height;
		this.mapArea = width * height;
		this.storage = (T[])Array.newInstance(cls, mapArea);	
	}

	public boolean contains(int X, int Y) {
		X -= Xoffset; Y -= Yoffset;
		return !(X < 0 || Y < 0 || X >= mapWidth || Y >= mapHeight);
	}
	
	private int getIndex(int X, int Y) {
		if (!contains(X, Y)) {
			throw new InvalidParameterException(String.format("Point (%d,%d) is outside of bounds [%d,%d]-[%d,%d].", X, Y, getX0(), getY0(), getX1(), getY1()));
		}
		return (Y - Yoffset) * mapWidth + X - Xoffset;
	}
	
	/*
	 * Methods
	 */
	public void process(HeightmapCallback callback) {
		int index = 0;
		for (int Y = Yoffset; Y < mapHeight + Yoffset; Y++) {
			for (int X = Xoffset; X < mapWidth + Xoffset; X++) {
				if (!callback.Process(X, Y, storage[index]))
					return;
				index++;
			}
		}
	}
	
	public void expandToInclude(int X, int Y) {
		if (contains(X, Y))
			return;	//nothing to do here.
		if (parent == null)
			throw new InvalidParameterException("Cannot expand a parent/root heightmap.");
		int bound[] = new int[] {getX0(), getY0(), getX1(), getY1()};
		int distPre[] = new int[] {
				bound[0] - X + X > bound[0] ? parent.mapWidth : 0,
				bound[1] - Y + Y > bound[1] ? parent.mapWidth : 0,
				X - bound[2] + X > bound[2] ? parent.mapWidth : 0,
				Y - bound[3] + Y > bound[3] ? parent.mapWidth : 0
		};
		int dist[] = new int[] {0, 0, 0, 0};
		int newWidth = mapWidth, newHeight = mapHeight;
		if (X - Xoffset < 0 || X - Xoffset >= mapWidth) {
			dist[0] = distPre[0] <  distPre[2] ? distPre[0] : 0;
			dist[2] = distPre[2] <= distPre[0] ? distPre[2] : 0;
			bound[0] = bound[0] - dist[0] + (dist[0] > bound[0] ? parent.mapWidth : 0);
			bound[2] = (bound[2] + dist[2]) % parent.mapWidth;
			newWidth = mapWidth + dist[0] + dist[2];
		}
		if (Y - Yoffset < 0 || Y - Yoffset >= mapHeight) {
			dist[1] = distPre[1] <  distPre[3] ? distPre[1] : 0;
			dist[3] = distPre[3] <= distPre[1] ? distPre[3] : 0;
			bound[1] = bound[1] - dist[1] + (dist[1] > bound[1] ? parent.mapWidth : 0);
			bound[3] = (bound[3] + dist[3]) % parent.mapHeight;
			newHeight = mapHeight + dist[1] + dist[3];
		}
		if (newWidth != mapWidth || newHeight != mapHeight) {
			
			T[] newStorage = (T[])Array.newInstance(storage.getClass(), mapArea);
			for (int row = 0; row < mapHeight; row++) {
				int posDest = (dist[1] + row) * newWidth + dist[0];
				int posSrc = row * mapWidth;
				
				System.arraycopy(storage, posSrc, newStorage, posDest, mapWidth);
			}
			storage = newStorage;
			Xoffset = bound[0]; Yoffset = bound[1];
			mapWidth = newWidth;
			mapHeight = newHeight;
		}
	}
	
	public void SavePNG(String fileName) throws IOException {
		BufferedImage bi = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		for (int y = 0; y < mapHeight; y++) {
			for (int x = 0; x < mapWidth; x++) {
				g.setColor(new Color(Color.HSBtoRGB(1.0f, 0.0f, (Float) storage[getIndex(x, y)])));
				g.drawLine(x, y, x, y);
			}
		}
		File o = new File(fileName);
		ImageIO.write(bi, "PNG", o);
	}
}
