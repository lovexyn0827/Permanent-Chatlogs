package lovexyn0827.chatlog.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {
	OptionType type();
	String defaultValue();
	String[] suggestions() default {};
	boolean experimental() default false;
}
