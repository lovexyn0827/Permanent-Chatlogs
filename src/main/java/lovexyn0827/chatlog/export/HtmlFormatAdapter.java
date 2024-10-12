package lovexyn0827.chatlog.export;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang3.StringEscapeUtils;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.Session.Line;
import lovexyn0827.chatlog.Session.Summary;
import lovexyn0827.chatlog.i18n.I18N;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;

@SuppressWarnings("deprecation")
final class HtmlFormatAdapter extends FormatAdapter {
	public static final Factory<HtmlFormatAdapter> FACTORY = new FormatAdapter.Factory<>("html") {
		@Override
		public HtmlFormatAdapter create(Writer out, Summary summary, Session session, ExportConfig config) {
			return new HtmlFormatAdapter(out, summary, session, config);
		}
	};
	List<String> spanIds;
	private int spanCount = 0;
	Map<String, HoverEvent> hoverEventsBySpan;
	Map<String, ClickEvent> clickEventsBySpan;
	XMLStreamWriter html;
	
	public HtmlFormatAdapter(Writer out, Summary summary, Session session, ExportConfig config) {
		super(out, summary, session, config);
	}
	
	@Override
	public void write() throws XMLStreamException, FactoryConfigurationError, IOException {
		this.directOut.append("<!DOCTYPE html>");
		this.html = XMLOutputFactory.newFactory().createXMLStreamWriter(this.directOut);
		this.html.writeStartElement("html");
		this.writeHead();
		this.html.writeStartElement("body");
		this.writeStartingDiv("main_container");
		this.spanIds = new ArrayList<>();
		this.hoverEventsBySpan = new HashMap<>();
		this.clickEventsBySpan = new HashMap<>();
		this.session.getAllMessages().forEach((l) -> {
			try {
				this.handleLine(l);
			} catch (XMLStreamException e) {
				LOGGER.error("Failed to handle message: {}", l.message);
				e.printStackTrace();
			}
		});
		this.writeFooter();
		this.buildJavaScript();
		this.html.writeEndElement();	// main_container
		this.html.writeEndElement();	// body
		this.html.writeEndElement();	// html
	}

	private String nextSpanId() {
		String spanId = "t_" + this.spanCount++;
		this.spanIds.add(spanId);
		return spanId;
	}
	
	void buildJavaScript() throws IOException, XMLStreamException {
		this.html.writeStartElement("script");
		this.html.writeCharacters("\n");
		StringBuilder js = new StringBuilder();
		buildEventMap(js);
		js.append(Files.readString(FabricLoader.getInstance().getModContainer("permanent-chat-logs")
				.get().getRootPaths().get(0).resolve("assets/textEventHandler.js")));
		this.directOut.append(js);
		this.html.writeEndElement();
	}
	
	private void buildEventMap(StringBuilder js) {
		js.append("const hoverEvents = {");
		this.hoverEventsBySpan.forEach((span, e) -> {
			js.append(span).append(": {");
			js.append("action: \"");
			js.append(e.getAction().asString());
			js.append("\", ");
			js.append("value: \"");
			serializeHoverEventValue(js, e.getValue(e.getAction()));
			js.append("\"},");
		});
		if (js.codePointAt(js.length() - 1) == ',') {
			js.deleteCharAt(js.length() - 1);
		}
		
		js.append("}; ");
		js.append("const clickEvents = {");
		this.clickEventsBySpan.forEach((span, e) -> {
			js.append(span).append(": {");
			js.append("action: \"");
			js.append(e.getAction().asString());
			js.append("\", ");
			js.append("value: \"");
			js.append(StringEscapeUtils.escapeJava(e.getValue()));
			js.append("\"},");
		});
		if (js.codePointAt(js.length() - 1) == ',') {
			js.deleteCharAt(js.length() - 1);
		}
		
		js.append("};");
	}
	
	private static void serializeHoverEventValue(StringBuilder js, Object val) {
		if (val instanceof Text) {
			text2StringIgnoringEvents(js, (Text) val);
		} else if (val instanceof HoverEvent.EntityContent) {
			text2StringIgnoringEvents(js, Texts.join(((HoverEvent.EntityContent) val).asTooltip(), (t) -> t));
		} else if (val instanceof HoverEvent.ItemStackContent) {
			HoverEvent.ItemStackContent itemVal = (HoverEvent.ItemStackContent) val;
			text2StringIgnoringEvents(js, itemVal.asStack().toHoverableText());
		}
	}
	
	private static void writeStringWithNewLines(String str, XMLStreamWriter html) throws XMLStreamException {
		String[] segs = str.split("\\n");
		for (int i = 0; i < segs.length; i++) {
			html.writeCharacters(segs[i]);
			if (i < segs.length - 1) {
				html.writeEmptyElement("br");
			}
		}
		
		if (str.endsWith("\n")) {
			html.writeEmptyElement("br");
		}
	}

	private static void text2StringIgnoringEvents(StringBuilder buf, Text t) {
		StringWriter out = new StringWriter();
		XMLStreamWriter html;
		try {
			html = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
		} catch (XMLStreamException | FactoryConfigurationError e1) {
			e1.printStackTrace();
			buf.append("Error converting text to string!");
			// XXX Throw when failing to export events?
			return;
		}
		
		t.visit((style, str) -> {
			int depth = 0;
			try {
				if (style.isBold()) {
					html.writeStartElement("b");
					depth++;
				}
				
				if (style.isItalic()) {
					html.writeStartElement("i");
					depth++;
				}
				
				if (style.isStrikethrough()) {
					html.writeStartElement("del");
					depth++;
				}

				if (style.getColor() != null) {
					html.writeStartElement("span");
					html.writeAttribute("style", String.format("color: #%6x", style.getColor().getRgb() & 0xFFFFFF));
					depth++;
				}
				
				writeStringWithNewLines(str, html);
				for (int i = 0; i < depth; i++) {
					html.writeEndElement();
				}
			} catch (XMLStreamException e) {
				LOGGER.error("Failed to export text: {}", t);
				e.printStackTrace();
			}
			
			return Optional.empty();
		}, Style.EMPTY);
		buf.append(StringEscapeUtils.escapeJava(out.getBuffer().toString()));
	}
	
	void handleLine(Line l) throws XMLStreamException {
		this.writeStartingDiv("message");
		this.writeStartingDiv("msg_marker");
		this.html.writeAttribute("style", String.format("background: #%6x", l.getMarkColor() & 0xFFFFFF));
		this.html.writeAttribute("data-sent-time", Long.toString(l.time));
		//this.html.writeAttribute("data-sender", l.sender.toString());
		this.html.writeEndElement();
		this.writeStartingDiv("msg_text");
		l.message.visit((style, str) -> {
			int depth = 0;
			try {
				if (style.isBold()) {
					this.html.writeStartElement("b");
					depth++;
				}
				
				if (style.isItalic()) {
					this.html.writeStartElement("i");
					depth++;
				}
				
				if (style.isStrikethrough()) {
					this.html.writeStartElement("del");
					depth++;
				}
				
				ClickEvent ce = style.getClickEvent();
				HoverEvent he = style.getHoverEvent();
				String spanId = this.nextSpanId();
				this.writeStartingSpan(spanId);
				if (style.getColor() != null) {
					this.html.writeAttribute("style", 
							String.format("color: #%6x", style.getColor().getRgb() & 0xFFFFFF));
				}
				
				depth++;
				if (ce != null) {
					this.clickEventsBySpan.put(spanId, ce);	
				}
			
				if (he != null) {
					this.hoverEventsBySpan.put(spanId, he);
				}
				
				writeStringWithNewLines(str, this.html);
				for (int i = 0; i < depth; i++) {
					this.html.writeEndElement();
				}
			} catch (XMLStreamException e) {
				LOGGER.error("Failed to export text: {}", l.message);
				e.printStackTrace();
			}
			
			return Optional.empty();
		}, Style.EMPTY);
		this.html.writeEndElement();
		this.html.writeEndElement();
	}
	
	private void writeHead() throws XMLStreamException, IOException {
		this.html.writeStartElement("head");
		this.html.writeStartElement("title");
		this.html.writeCharacters(I18N.translate("export.this.html.title", 
				this.sessionMeta.saveName, 
				this.sessionMeta.size, 
				this.sessionMeta.getFormattedStartTime()));
		this.html.writeEndElement();
		this.writeStyleSheet();
		this.html.writeEmptyElement("meta");
		this.html.writeAttribute("charset", "utf-8");
		this.html.writeEndElement();
	}
	
	// To ensure that tooltips of the last line display well in most cases.
	private void writeFooter() throws XMLStreamException, IOException {
		this.writeStartingDiv("footer");
		this.html.writeCharacters(I18N.translate("export.this.html.footer1", 
				LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
		this.html.writeStartElement("a");
		this.html.writeAttribute("href", "https://modrinth.com/mod/permanent-chatlogs");
		ModMetadata modMeta = FabricLoader.getInstance().getModContainer("permanent-chat-logs").get().getMetadata();
		this.html.writeCharacters(modMeta.getName() + " " + modMeta.getVersion());
		this.html.writeEndElement();
		this.html.writeEmptyElement("br");
		this.html.writeCharacters(I18N.translate("export.this.html.footer2", 
				MinecraftClient.getInstance().getGameProfile().getName()));
		this.html.writeEndElement();
	}
	
	void writeStartingDiv(String clazz) throws XMLStreamException {
		this.html.writeStartElement("div");
		this.html.writeAttribute("class", clazz);
	}
	
	private void writeStartingSpan(String id) throws XMLStreamException {
		this.html.writeStartElement("span");
		this.html.writeAttribute("id", id);
	}

	private void writeStyleSheet() throws XMLStreamException, IOException {
		this.html.writeStartElement("style");
		this.html.writeCharacters(Files.readString(FabricLoader.getInstance().getModContainer("permanent-chat-logs")
				.get().getRootPaths().get(0).resolve("assets/defaultStyle.css")));
		this.html.writeEndElement();
	}
}
