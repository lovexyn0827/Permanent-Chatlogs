package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.Session;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.LanServerInfo;
import net.minecraft.client.network.ServerInfo;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {
	@Shadow
	protected MultiplayerServerListWidget serverListWidget;
	
	@Inject(method = "connect", at = @At("HEAD"))
	private void onConnect(CallbackInfo ci) {
		MultiplayerServerListWidget.Entry entry =  this.serverListWidget.getSelectedOrNull();
		if(entry instanceof MultiplayerServerListWidget.ServerEntry) {
			ServerInfo info = ((MultiplayerServerListWidget.ServerEntry) entry).getServer();
			Session.current = new Session(info.name);
		} else if(entry instanceof MultiplayerServerListWidget.LanServerEntry) {
			LanServerInfo info = ((MultiplayerServerListWidget.LanServerEntry) entry).getLanServerEntry();
			Session.current = new Session(info.getMotd());
		}
	}
}
