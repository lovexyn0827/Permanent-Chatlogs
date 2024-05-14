package lovexyn0827.chatlog;

import net.fabricmc.api.ModInitializer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PermanentChatLogMod implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("permanent-chat-logs");
    public static final ThreadLocal<Boolean> PERMISSIVE_EVENTS = ThreadLocal.withInitial(() -> false);
    
	@Override
	public void onInitialize() {
	}
}