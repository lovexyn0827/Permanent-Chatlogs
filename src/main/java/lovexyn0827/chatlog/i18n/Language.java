package lovexyn0827.chatlog.i18n;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

/**
 * A language definition. Only the in-game contents will be translated.
 */
class Language {
	private final String readableName;
	private final Map<String, String> translations = Maps.newHashMap();
	private final String id;
	
	/**
	 * Create a new @{code Language} instance using the definition in assets/lang/@{code name}.json
	 * @param id Can be something like en_us, zh_cn, and so on. 
	 * @throws IOException When
	 */
	public Language(String id) throws Exception {
		this.id = id;
		Path langFile = FabricLoader.getInstance().getModContainer("permanent-chat-logs")
				.get().getRootPaths().get(0).resolve("assets/lang/" + id + ".json");
		try {
			@SuppressWarnings("deprecation")
			JsonObject def = new JsonParser()
					.parse(new String(Files.readAllBytes(langFile), Charset.forName("GBK")))
					.getAsJsonObject();
			this.readableName = def.get("readableName").getAsString();
			def.getAsJsonObject("translations")
					.entrySet()
					.forEach((e) -> this.translations.put(e.getKey(), e.getValue().getAsString()));
		} catch (Exception e) {
			I18N.LOGGER.error("Failed to load the definition of language " + id);
			e.printStackTrace();
			throw e;
		}
	}
	
	/**
	 * @param key The translation key
	 * @return The translated content or the translation key if the key is undefined.
	 */
	public String translate(String key) {
		if(!this.translations.containsKey(key)) {
			return key; 
		}
		
		return this.translations.get(key);
	}
	
	public String getName() {
		return this.readableName;
	}
	
	/**
	 * Compare translation keys against en_us.json, to find absent and redundancy keys.
	 * @return {@code true} if all the translation keys here is also in en_us.json, 
	 * and all keys in en_us.json is also here, {@code false} otherwise.
	 */
	public boolean validate() {
		if("en_us".equals(this.id)) {
			return true;
		} else {
			Set<String> here = this.translations.keySet();
			Set<String> en = I18N.EN_US.translations.keySet();
			if(here.containsAll(en) && en.containsAll(here)) {
				return true;
			} else {
				en.stream()
						.filter((key) -> !here.contains(key))
						.forEach((key) -> I18N.LOGGER.warn("Absence: " + key));
				here.stream()
						.filter((key) -> !en.contains(key))
						.forEach((key) -> I18N.LOGGER.warn("Redunancy: " + key));
				return false;
			}
		}
	}

	public boolean containsKey(String mayKey) {
		return this.translations.containsKey(mayKey);
	}
}
