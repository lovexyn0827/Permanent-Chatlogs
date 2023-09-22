package lovexyn0827.chatlog.config;

import java.util.function.Function;

public enum OptionType {
	BOOLEAN(Boolean::parseBoolean), 
	INTEGER(Integer::parseInt), 
	FLOAT(Float::parseFloat);
	
	final Function<String, ?> parser;
	
	private OptionType(Function<String, ?> parser) {
		this.parser = parser;
	}
}
