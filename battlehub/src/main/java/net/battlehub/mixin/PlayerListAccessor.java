package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.players.PlayerList;

/** The server's player limit, set by the self-test to that of a world opened to LAN (eight). */
@Mixin(PlayerList.class)
public interface PlayerListAccessor {
	@Accessor("maxPlayers")
	@Mutable
	void battlehub$setMaxPlayers(int max);
}
