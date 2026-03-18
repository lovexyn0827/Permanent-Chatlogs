package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.session.SessionRecorder;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;

@Mixin(InGameHud.class)
public class InGameHudMixin {
	@Inject(
			method = "setOverlayMessage", 
			at = @At("HEAD")
	)
	private void onOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
		SessionRecorder.current().addOverlayMessage(message, tinted, System.currentTimeMillis());
	}
	
	@Inject(
			method = "setTitle", 
			at = @At("HEAD")
	)
	private void onTitle(Text message, CallbackInfo ci) {
		SessionRecorder.current().addTitle(message, System.currentTimeMillis());
	}
	
	@Inject(
			method = "setSubtitle", 
			at = @At("HEAD")
	)
	private void onSubtitle(Text message, CallbackInfo ci) {
		SessionRecorder.current().addSubtitle(message, System.currentTimeMillis());
	}
}
