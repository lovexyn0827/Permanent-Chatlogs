package lovexyn0827.chatlog.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.config.Options;

@Mixin(value = ChatHud.class, priority = 2023)
public class ChatHudMixin {
	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;"
			+ "ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V", at = @At("HEAD"))
	private void onMessage(Text message, @Nullable MessageSignatureData signature, int ticks, @Nullable MessageIndicator indicator, boolean refresh, CallbackInfo info) {
		if(Session.current != null) {
			Session.current.onMessage(Util.NIL_UUID, message);
		}
	}
	
	@ModifyConstant(
			method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;"
					+ "ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V", 
			constant = @Constant(intValue = 100)
	)
	private int overrideMaxMessages(int initial) {
		return Options.visibleLineCount;
	}
}