package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;

/** Lobby leaderboards: text displays that exist only on the clients, built from these data fields. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
	@Accessor("DATA_TEXT_ID")
	static EntityDataAccessor<Component> battlehub$text() {
		throw new AssertionError();
	}

	@Accessor("DATA_LINE_WIDTH_ID")
	static EntityDataAccessor<Integer> battlehub$lineWidth() {
		throw new AssertionError();
	}

	@Accessor("DATA_BACKGROUND_COLOR_ID")
	static EntityDataAccessor<Integer> battlehub$background() {
		throw new AssertionError();
	}

	@Accessor("DATA_STYLE_FLAGS_ID")
	static EntityDataAccessor<Byte> battlehub$styleFlags() {
		throw new AssertionError();
	}
}
