package lovexyn0827.chatlog.mixin;

import lovexyn0827.chatlog.session.Session;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.session.SessionRecorder;

@Mixin(value = ChatHud.class, priority = 2023)
public class ChatHudMixin {
	private static final BooleanSupplier SHOULD_ADD_MESSAGE = Util.make(() -> {
		try {
			Class<?> chatLogCl = Class.forName("obro1961.chatpatches.chatlog.ChatLog");
			Field suspended = chatLogCl.getDeclaredField("suspended");
			suspended.setAccessible(true);
			MethodHandle mh = MethodHandles.lookup().unreflectGetter(suspended);
			return () -> {
				try {
					return !(boolean) mh.invoke();
				} catch (Throwable e) {
					return true;
				}
			};
		} catch (Throwable e) {
			e.printStackTrace();
			return () -> true;
		}
	});
	
	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;"
			+ "Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
	private void onMessage(Text message, @Nullable MessageSignatureData signature, @Nullable MessageIndicator indicator, CallbackInfo info) {
		if(SessionRecorder.current() != null && SHOULD_ADD_MESSAGE.getAsBoolean()) {
			SessionRecorder.current().onMessage(Util.NIL_UUID, message);
		}
	}
}
