package lovexyn0827.chatlog;

import net.fabricmc.api.ModInitializer;

import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PermanentChatLogMod implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("permanent-chat-logs");
    public static final ThreadLocal<Boolean> PERMISSIVE_EVENTS = ThreadLocal.withInitial(() -> false);
    
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
	@Override
	public void onInitialize() {
	}
}