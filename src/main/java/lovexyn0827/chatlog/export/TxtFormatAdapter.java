package lovexyn0827.chatlog.export;

import java.io.PrintWriter;
import java.io.Writer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import lovexyn0827.chatlog.i18n.I18N;
import lovexyn0827.chatlog.session.Session;
import lovexyn0827.chatlog.session.Session.Summary;

class TxtFormatAdapter extends FormatAdapter {
	public static final Factory<TxtFormatAdapter> FACTORY = new FormatAdapter.Factory<>("txt") {
		@Override
		public TxtFormatAdapter create(Writer out, Summary summary, Session session, ExportConfig config) {
			return new TxtFormatAdapter(out, summary, session, config);
		}
	};
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public TxtFormatAdapter(Writer out, Summary summary, Session session, ExportConfig config) {
		super(out, summary, session, config);
	}

	@Override
	public void write() {
		PrintWriter pw = new PrintWriter(this.directOut);
		pw.println();
		pw.println(I18N.translate("gui.filter.savename") + ": " + this.sessionMeta.saveName);
		pw.println(I18N.translate("gui.filter.date") + ": " + Instant.ofEpochMilli(this.sessionMeta.startTime)
			.atZone(this.sessionMeta.timeZone.toZoneId()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		pw.println(I18N.translate("gui.filter.messages") + ": " + this.sessionMeta.size);
		pw.println();
		this.session.getMessages().forEach((l) -> {
			if (config.includeTimeOfMsgs()) {
				pw.printf("[%s]", TIME_FORMAT.format(Instant.ofEpochMilli(l.time)
						.atZone(this.sessionMeta.timeZone.toZoneId())));
			}
			
			if (config.includeSender()) {
				pw.printf("[%s]", l.sender);	// XXX: Is including UUID appropriate?
			}
			
			pw.println(l.message.getString());
		});
	}
}
