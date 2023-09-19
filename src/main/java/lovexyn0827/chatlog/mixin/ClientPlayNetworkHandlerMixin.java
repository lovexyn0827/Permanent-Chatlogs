package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.Session;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Inject(method = "onDisconnect",at = @At(value = "RETURN"))
	private void onPlayerDisconnect(DisconnectS2CPacket packet, CallbackInfo ci) {
		if(Session.current != null) {
			Session.current.saveAndClose();
			Session.current = null;
		}
	}
}
