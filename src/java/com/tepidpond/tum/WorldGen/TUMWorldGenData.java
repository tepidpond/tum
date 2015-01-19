package com.tepidpond.tum.WorldGen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.tepidpond.tum.G;
import com.tepidpond.tum.PlateTectonics.Lithosphere;

public class TUMWorldGenData extends WorldSavedData {
	private static final String tagWorldGen = G.ModID;
	private NBTTagCompound data = new NBTTagCompound();
	private final String tagName;
	
	/* Region: Items saved in TUM.WorldGen.Settings */
	private int   mapSize =       G.WorldGen.DefaultMapSize;
	private float landSeaRatio =  G.WorldGen.DefaultLandSeaRatio;
	private int   erosionPeriod = G.WorldGen.DefaultErosionPeriod;
	private float foldingRatio =  G.WorldGen.DefaultFoldingRatio;
	private int   aggrRatioAbs =  G.WorldGen.DefaultAggrRatioAbs;
	private float aggrRatioRel =  G.WorldGen.DefaultAggrRatioRel;
	private int   maxCycles =     G.WorldGen.DefaultMaxCycles;
	private int   numPlates =     G.WorldGen.DefaultNumPlates;
	private int   maxGens =       G.WorldGen.DefaultMaxGens;
	
	/* Region: Items saved in TUM.WorldGen.Storage */
	private float[] heightMap;
	private boolean heightMapGenerated = false;
	
	public TUMWorldGenData() {
		super(tagWorldGen);
		this.tagName = tagWorldGen;
	}
	
	public TUMWorldGenData(String tagName) {
		super(tagName);
		this.tagName = tagName;
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		NBTTagCompound nbtWorldGen = compound.getCompoundTag("WorldGen");
		NBTTagCompound nbtSettings = nbtWorldGen.getCompoundTag("Settings");
		if (nbtSettings != null) {
			if (nbtSettings.hasKey("mapSize"))       mapSize =       nbtSettings.getInteger("mapSize");
			if (nbtSettings.hasKey("landSeaRatio"))  landSeaRatio =  nbtSettings.getFloat("landSeaRatio");
			if (nbtSettings.hasKey("erosionPeriod")) erosionPeriod = nbtSettings.getInteger("erosionPeriod");
			if (nbtSettings.hasKey("foldingRatio"))  foldingRatio =  nbtSettings.getFloat("foldingRatio");
			if (nbtSettings.hasKey("aggrRatioAbs"))  aggrRatioAbs =  nbtSettings.getInteger("aggrRatioAbs");
			if (nbtSettings.hasKey("aggrRatioRel"))  aggrRatioRel =  nbtSettings.getFloat("aggrRatioRel");
			if (nbtSettings.hasKey("maxCycles"))     maxCycles =     nbtSettings.getInteger("maxCycles");
			if (nbtSettings.hasKey("numPlates"))     numPlates =     nbtSettings.getInteger("numPlates");
			if (nbtSettings.hasKey("maxGens"))       maxGens =       nbtSettings.getInteger("maxGens");
		}

		NBTTagCompound nbtStorage = nbtWorldGen.getCompoundTag("Storage");
		float[] heightMap = new float[(int) Math.pow(mapSize, 2)];
		if (nbtStorage != null && nbtStorage.hasKey("heightMap") && nbtStorage.hasKey("heightMapGenerated")) {
			// Load packed heightmap from NBT
			byte[] byteArray = nbtStorage.getByteArray("heightMap");
			heightMapGenerated = nbtStorage.getBoolean("heightMapGenerated");
			if (byteArray.length == heightMap.length * 4 && heightMapGenerated) {
				DataInputStream dis = new DataInputStream(new ByteArrayInputStream(byteArray));
				try {
					for(int i=0; i < heightMap.length; i++) {
						heightMap[i] = dis.readFloat();							
					}
					this.heightMap = heightMap;
				} catch (IOException e) {
					// There is no need to be upset. Just regenerate it. An extra minute at world
					// load time is only annoying.
					nbtStorage.removeTag("heightMap");
					heightMapGenerated = false;
				}
			} else {
				heightMapGenerated = false;
			}
		} else {
			heightMapGenerated = false;
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound compound) {
		NBTTagCompound nbtWorldGen = new NBTTagCompound();
		NBTTagCompound nbtSettings = new NBTTagCompound();
		nbtSettings.setInteger("mapSize",       mapSize);
		nbtSettings.setFloat(  "landSeaRatio",  landSeaRatio);
		nbtSettings.setInteger("erosionPeriod", erosionPeriod);
		nbtSettings.setFloat(  "foldingRatio",  foldingRatio);
		nbtSettings.setInteger("aggrRatioAbs",  aggrRatioAbs);
		nbtSettings.setFloat(  "aggrRatioRel",  aggrRatioRel);
		nbtSettings.setInteger("maxCycles",     maxCycles);
		nbtSettings.setInteger("numPlates",     numPlates);
		nbtSettings.setInteger("maxGens",       maxGens);
		nbtWorldGen.setTag("Settings", nbtSettings);
		
		if (heightMapGenerated) {
			NBTTagCompound nbtStorage = new NBTTagCompound();
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(baos);
				for(int i = 0; i < heightMap.length; i++) {
					dos.writeFloat(heightMap[i]);
				}
				nbtStorage.setByteArray("heightMap", baos.toByteArray());
				nbtStorage.setBoolean("heightMapGenerated", heightMapGenerated);
				nbtWorldGen.setTag("Storage", nbtStorage);
			} catch (IOException e) {
				// This is a problem.
				heightMapGenerated = false;
				assert false: "Cause for panic, unable to save HeightMap to NBT";
			}
		}
		compound.setTag("WorldGen", nbtWorldGen);
	}
	
	public static TUMWorldGenData get(World world) {
		TUMWorldGenData data = (TUMWorldGenData)world.loadItemData(TUMWorldGenData.class, tagWorldGen);
		if (data == null) {
			data = new TUMWorldGenData(tagWorldGen);
			world.setItemData(tagWorldGen, data);
		}
		return data;
	}

	public float[] getHeightMap() {
		if (heightMapGenerated) return heightMap;
		return null;
	}
	public boolean isHeightMapGenerated() {
		return heightMapGenerated;
	}
	public void setHeightMap(float heightMap[], int mapSize) {
		if ((heightMap.length & -heightMap.length) == heightMap.length && heightMap.length == Math.pow(mapSize, 2)) {
			this.mapSize = mapSize;
			this.heightMap = heightMap;
			heightMapGenerated = true;
			this.markDirty();
		}
	}
	
	public int getMapSize() {
		return mapSize;
	}

	public void setMapSize(int mapSize) {
		if (mapSize != this.mapSize)
			heightMapGenerated = false;
		this.mapSize = mapSize;
		this.markDirty();
	}

	public float getLandSeaRatio() {
		return landSeaRatio;
	}

	public void setLandSeaRatio(float landSeaRatio) {
		this.landSeaRatio = landSeaRatio;
		this.markDirty();
	}

	public int getErosionPeriod() {
		return erosionPeriod;
	}

	public void setErosionPeriod(int erosionPeriod) {
		this.erosionPeriod = erosionPeriod;
		this.markDirty();
	}

	public float getFoldingRatio() {
		return foldingRatio;
	}

	public void setFoldingRatio(float foldingRatio) {
		this.foldingRatio = foldingRatio;
		this.markDirty();
	}

	public int getAggrRatioAbs() {
		return aggrRatioAbs;
	}

	public void setAggrRatioAbs(int aggrRatioAbs) {
		this.aggrRatioAbs = aggrRatioAbs;
		this.markDirty();
	}

	public float getAggrRatioRel() {
		return aggrRatioRel;
	}

	public void setAggrRatioRel(float aggrRatioRel) {
		this.aggrRatioRel = aggrRatioRel;
		this.markDirty();
	}

	public int getMaxCycles() {
		return maxCycles;
	}

	public void setMaxCycles(int maxCycles) {
		this.maxCycles = maxCycles;
		this.markDirty();
	}

	public int getNumPlates() {
		return numPlates;
	}

	public void setNumPlates(int numPlates) {
		this.numPlates = numPlates;
		this.markDirty();
	}

	public int getMaxGens() {
		return maxGens;
	}

	public void setMaxGens(int maxGens) {
		this.maxGens = maxGens;
		this.markDirty();
	}
}
