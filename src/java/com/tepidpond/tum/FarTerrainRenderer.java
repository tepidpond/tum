package com.tepidpond.tum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FarTerrainRenderer extends IRenderHandler {
	private final Minecraft mc;
	private final TextureManager renderEngine;
	private RenderManager renderManager;
	
	public FarTerrainRenderer(Minecraft mc) {
		this.mc = mc;
		this.renderEngine = mc.getTextureManager();
		this.renderManager = RenderManager.instance;
	}
	
	@SubscribeEvent
	public void RenderWorldLastEvent(RenderWorldLastEvent event) {
		try {
			if (mc.thePlayer != null) {
				render(event.partialTicks, mc.theWorld, mc);
			}
		} catch (Exception e) {
			System.out.println("Non-fatal error in FarTerrainRenderer.\n" + e.toString());
		}
	}
	
	@Override
	public void render(float partialTicks, WorldClient world, Minecraft mc) {
		// Completely POC code.
		
		float x = 100.0f;
		float y = 100.0f;
		float z = 100.0f;
		String[] text = {"AMAZING!"};
		boolean renderBlackBackground = false;
		int color = 0xff00ff;
		
        float playerX = (float) (mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks);
        float playerY = (float) (mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks);
        float playerZ = (float) (mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks);

        float dx = x-playerX;
        float dy = y-playerY;
        float dz = z-playerZ;
        float distance = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        float scale = 0.03f;
        
        GL11.glColor4f(1f, 1f, 1f, 0.5f);
        GL11.glPushMatrix();
        GL11.glTranslatef(dx, dy, dz);
        GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        int textWidth = 0;
        for (String thisMessage : text)
        {
            int thisMessageWidth = mc.fontRenderer.getStringWidth(thisMessage);

            if (thisMessageWidth > textWidth)
            	textWidth = thisMessageWidth;
        }
        
        int lineHeight = 10;
        
        if(renderBlackBackground)
        {
            Tessellator tessellator = Tessellator.instance;
        	
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            tessellator.startDrawingQuads();
            int stringMiddle = textWidth / 2;
            tessellator.addVertex(-stringMiddle - 1, -1 + 0, 0.0D);
            tessellator.addVertex(-stringMiddle - 1, 8 + lineHeight*text.length-lineHeight, 0.0D);
            tessellator.addVertex(stringMiddle + 1, 8 + lineHeight*text.length-lineHeight, 0.0D);
            tessellator.addVertex(stringMiddle + 1, -1 + 0, 0.0D);
            tessellator.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
        
        int i = 0;
        for(String message : text)
        {
        	mc.fontRenderer.drawString(message, -textWidth / 2, i*lineHeight, color);
        	i++;
        }
        
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();		
	}
}
