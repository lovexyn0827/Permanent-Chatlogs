package lovexyn0827.chatlog.gui;

import lovexyn0827.chatlog.i18n.I18N;
import lovexyn0827.chatlog.session.Session;
import lovexyn0827.chatlog.session.SessionRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.toast.SystemToast;

final class GuiUtils {
	static boolean loadSession(MinecraftClient mc, Session.Summary summary, Screen parentScreen) {
		return GuiUtils.loadSession(mc, summary, parentScreen, -1);
	}
	
	static boolean loadSession(MinecraftClient mc, Session.Summary summary, Screen parentScreen, int ordinalInSession) {
		if (SessionRecorder.current() != null && SessionRecorder.current().getId() == summary.id) {
			SystemToast warning = new SystemToast(new SystemToast.Type(), 
					I18N.translateAsText("gui.sload.failongoing"), 
					I18N.translateAsText("gui.sload.failongoing.desc"));
			MinecraftClient.getInstance().getToastManager().add(warning);
		}
		
		try {
			Session session = summary.load();
			if (session != null) {
				ChatLogScreen screen = new ChatLogScreen(summary, session, parentScreen);
				mc.setScreen(screen);
				if (ordinalInSession > 0) {
					screen.scrollTo(ordinalInSession);
				}
			} else {
				SystemToast warning = new SystemToast(new SystemToast.Type(), 
						I18N.translateAsText("gui.sload.failure"), 
						I18N.translateAsText("gui.sload.failure.desc"));
				MinecraftClient.getInstance().getToastManager().add(warning);
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return true;
	}
}
