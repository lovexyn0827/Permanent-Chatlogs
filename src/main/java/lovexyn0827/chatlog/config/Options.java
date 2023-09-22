package lovexyn0827.chatlog.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Properties;

import lovexyn0827.chatlog.PermanentChatLogMod;

public final class Options {
	private static final File OPTION_FILE = new File("permanent-chatlogs.prop");
	private static final Properties OPTION_SET;

	@Option(type = OptionType.INTEGER, defaultValue = "65535")
	public static int visibleLineCount = 65535;
	
	@Option(type = OptionType.INTEGER, defaultValue = "100000")
	public static int autoSaveIntervalInMs = 100000;
	
	private Options() {}
	
	public static void save() {
		try(FileWriter fw = new FileWriter(OPTION_FILE)) {
			OPTION_SET.store(fw, "Options For Permanent Chat Logs");
		} catch (IOException e) {
			PermanentChatLogMod.LOGGER.error("Failed to save options!");
			e.printStackTrace();
		}
	}
	
	public static void set(String name, String value) {
		OPTION_SET.setProperty(name, value);
		try {
			Field f = Options.class.getField(name);
			Option o = f.getAnnotation(Option.class);
			f.set(null, o.type().parser.apply(value));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		save();
	}
	
	static {
		Properties prop = new Properties();
		if(OPTION_FILE.exists()) {
			try(FileReader fr = new FileReader(OPTION_FILE)) {
				prop.load(fr);
			} catch (IOException e) {
				PermanentChatLogMod.LOGGER.error("Failed to load options!");
				e.printStackTrace();
			}
		}
		
		for(Field f : Options.class.getDeclaredFields()) {
			Option o = f.getAnnotation(Option.class);
			if(o == null) {
				continue;
			}
			
			prop.computeIfAbsent(f.getName(), (n) -> o.defaultValue());
			try {
				f.set(null, o.type().parser.apply(prop.getProperty(f.getName())));
			} catch (Exception e) {
				PermanentChatLogMod.LOGGER.error("Option {} has incorrect value {}!", 
						f.getName(), prop.getProperty(f.getName()));
				e.printStackTrace();
				try {
					f.set(null, o.type().parser.apply(prop.getProperty(f.getName())));
				} catch (Exception e1) {
					throw new RuntimeException("Incorrect default value for " + f.getName(), e1);
				}
			}
		}
		
		OPTION_SET = prop;
		save();
	}
}
