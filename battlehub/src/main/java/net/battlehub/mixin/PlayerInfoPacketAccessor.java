package net.battlehub.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;

/** Lets the tab list per world (hub.TabListWorlds) send a player info packet with its own entries. */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoPacketAccessor {
	@Mutable
	@Accessor("entries")
	void battlehub$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
