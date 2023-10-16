package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.resource.language.LanguageManager;

@Mixin(LanguageManager.class)
public abstract class LanguageManagerMixin {
	@Inject(method = "setLanguage(Ljava/lang/String;)V",  at = @At("HEAD"))
	private void updateLanguage(String id, CallbackInfo ci) {
		I18N.setLanguage(id);
	}
}
