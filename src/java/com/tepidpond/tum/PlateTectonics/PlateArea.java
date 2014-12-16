package com.tepidpond.tum.PlateTectonics;

import java.util.Vector;

public class PlateArea {
	private Vector<Integer> border = new Vector(2048);
	private int mapSize;
	public int x0, y0, x1, y1;
	public int rgt() { return x1; }
	public int lft() { return x0; }
	public int btm() { return y1; }
	public int top() { return y0; }
	public int wdt() { return x1 - x0; }
	public int hgt() { return y1 - y0; }	
	public PlateArea(int mapTile, int mapSize) {
		this.mapSize = mapSize;
		this.x0 = Util.getX(mapTile, mapSize);
		this.x1 = Util.getX(mapTile, mapSize);
		this.y0 = Util.getY(mapTile, mapSize);
		this.y1 = Util.getY(mapTile, mapSize);
		border.addElement(mapTile);
	}
	public int getBorder(int index) {
		return border.get(index);
	}
	public void setBorder(int index, int mapTile) {
		if (index == borderSize()) {
			pushBorder(mapTile);
			updateBoundsToInclude(mapTile, mapSize);
		} else {
			border.setElementAt(mapTile, index);
		}
	}
	public int lastBorder() {
		return border.get(border.size() - 1);
	}
	public int popBorder() {
		int mapTile = border.lastElement();
		border.removeElementAt(border.size() - 1);
		return mapTile;
	}
	public void pushBorder(int mapTile) {
		border.addElement(mapTile);
		updateBoundsToInclude(mapTile, mapSize);
	}
	public int borderSize() {
		return border.size();
	}
	private void updateBoundsToInclude(int mapTile, int mapSize) {
		int x = Util.getX(mapTile, mapSize);
		int y = Util.getY(mapTile, mapSize);
		
		if (y > y1) y1 = y;
		if (y < y0) y0 = y;
		if (x > x1) x1 = x;
		if (x < x0) x0 = x;
	}
}
