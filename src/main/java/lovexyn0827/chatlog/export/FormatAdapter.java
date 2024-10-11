package lovexyn0827.chatlog.export;

import java.io.Writer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.Session.Summary;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.text.Text;

public abstract class FormatAdapter {
	protected static final Logger LOGGER = LogManager.getLogger();
	public static final ImmutableList<Factory<? extends FormatAdapter>> FORMAT_FACTORIES = 
			ImmutableList.<Factory<? extends FormatAdapter>>builder()
					.add(HtmlFormatAdapter.FACTORY)
					.add(TxtFormatAdapter.FACTORY)
					.build();

	protected final Writer directOut;
	protected final Summary sessionMeta;
	protected final Session session;
	protected final ExportConfig config;

	public FormatAdapter(Writer directOut, Summary sessionMeta, Session session, ExportConfig config) {
		this.directOut = directOut;
		this.sessionMeta = sessionMeta;
		this.session = session;
		this.config = config;
	}
	
	public abstract void write() throws Exception;
	
	public static abstract class Factory<T extends FormatAdapter> {
		private final String id;
		
		public Factory(String id) {
			this.id = id;
		}
		
		public abstract T create(Writer out, Summary summary, Session session, ExportConfig config);
		
		/**
		 * @return An internal identifier for corresponding format adapter. Also served as the file name extension.
		 */
		public final String getId() {
			return this.id;
		}
		
		public final String getExtension() {
			return this.id;
		}

		public final String getDescription() {
			return I18N.translate("gui.export." + this.getId());
		}
		
		public final Text getDisplayedText() {
			return Text.literal(String.format("%s (.%s)", this.getDescription(), this.getId()));
		}
	}
}