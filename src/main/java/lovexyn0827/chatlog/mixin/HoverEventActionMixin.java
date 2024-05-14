package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lovexyn0827.chatlog.PermanentChatLogMod;
import net.minecraft.text.HoverEvent;

@Mixin(HoverEvent.Action.class)
public class HoverEventActionMixin {
	@Inject(method = "isParsable", at = @At("HEAD"), cancellable = true)
	void fuckOjang(CallbackInfoReturnable<Boolean> cir) {
		if(PermanentChatLogMod.PERMISSIVE_EVENTS.get()) {
			cir.setReturnValue(true);
			cir.cancel();
		}
	}
}
