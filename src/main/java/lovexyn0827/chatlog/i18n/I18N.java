package lovexyn0827.chatlog.i18n;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableSet;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;

/**
 * Translation.
 * @author lovexyn0827
 * Date: April 10, 2022
 */
public class I18N {
	public static final Logger LOGGER = LogManager.getLogger();
	/**
	 * Remember to add the name of the added language to make it present in command suggestions
	 */
	public static final ImmutableSet<String> SUPPORTED_LANGUAGES = ImmutableSet.<String>builder()
			.add("zh_cn", "en_us").build();
	public static final Language EN_US;
	private static Language currentLanguage;
	
	public static String translate(String translationKey) {
		return currentLanguage.translate(translationKey);
	}

	public static String translate(String translationKey, Object ... args) {
		return String.format(translate(translationKey), args);
	}
	
	public static Text translateAsText(String translationKey) {
		return Text.literal(translate(translationKey));
	}

	public static Text translateAsText(String translationKey, Object ... args) {
		return Text.literal(translate(translationKey, args));
	}
	
	@SuppressWarnings("resource")
	public static boolean setLanguage(String name) {
		if(name == null) {
			if(MinecraftClient.getInstance().options != null) {
				String sysLang = MinecraftClient.getInstance().options.language;
				if(SUPPORTED_LANGUAGES.contains(sysLang)) {
					name = sysLang;
				} else {
					name = "en_us";
				} 
			} else {
				name = "en_us";
			}
		} else if(!SUPPORTED_LANGUAGES.contains(name)) {
			 name = "en_us";
		}
		
		try {
			Language lang = new Language(name);
			if(lang.validate()) {
				currentLanguage = lang;
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	static {
		try {
			EN_US = new Language("en_us");
			setLanguage(null);
		} catch (Exception e) {
			throw new CrashException(new CrashReport("Couldn't load the default translation.", e));
		}
	}
}
