package com.extremedimensions.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the Lurker on the long Enderman frame with its own cloak-and-mask texture, a little taller. The glowing
 * violet eyes come from the Enderman eye layer; the jaw, trembling and carried block are the Enderman's animations.
 */
public class LurkerRenderer extends EndermanRenderer {
	private static final Identifier TEXTURE = ExtremeDimensions.id("textures/entity/lurker.png");

	public LurkerRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public Identifier getTextureLocation(EndermanRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(EndermanRenderState state, PoseStack pose) {
		pose.scale(1.15f, 1.15f, 1.15f);
	}
}
