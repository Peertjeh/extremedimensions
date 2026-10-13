package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;

/** Only used by the self-test: lets simulated players have a channel that silently drops packets. */
@Mixin(Connection.class)
public interface ConnectionAccessor {
	@Accessor("channel")
	void battlehub$setChannel(Channel channel);
}
