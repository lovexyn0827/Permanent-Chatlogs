package lovexyn0827.chatlog.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.network.MessageType;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.config.Options;

@Mixin(ChatHud.class)
public class ChatHudMixin {
	@Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
	private void onMessage(Text message, CallbackInfo info) {
		if(Session.current != null) {
			Session.current.onMessage(MessageType.CHAT, Util.NIL_UUID, message);
		}
	}
	
	@ModifyConstant(
			method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", 
			constant = @Constant(intValue = 100)
	)
	private int overrideMaxMessages(int initial) {
		return Options.visibleLineCount;
	}
}