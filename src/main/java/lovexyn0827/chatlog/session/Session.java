package lovexyn0827.chatlog.session;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.MalformedJsonException;

import io.netty.util.internal.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lovexyn0827.chatlog.config.Options;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

// TODO Merge & Auto merge
public final class Session {
	static final Logger LOGGER = LogManager.getLogger("ChatlogSession");
	static final File CHATLOG_FOLDER = Util.make(() -> {
		File f = new File("chatlogs");
		boolean success;
		l: 
		if(!f.exists()) {
			success = f.mkdir();
		} else if(!f.isDirectory()) {
			for(int i = 0; i < 10; i++) {
				String renameTo = ("chatlogs" + System.currentTimeMillis()) + i;
				if(f.renameTo(new File(renameTo))) {
					LOGGER.warn("A non-directory file named 'chatlogs' already exists, renaming to {}.", renameTo);
					success = f.mkdir();
					break l;
				}
			}
			
			LOGGER.error("Failed to rename existing file {}, deleting", f.getAbsolutePath());
			if(f.delete()) {
				success = f.mkdir();
			} else {
				success = false;
			}
		} else {
			return f;
		}
		
		if(success) {
			return f;
		} else {
			LOGGER.fatal("Unable to create directory for chat logs.");
			throw new RuntimeException("Unable to create directory for chat logs.");
		}
	});
	static final File INDEX = new File(CHATLOG_FOLDER, "index.ssv");
	private static final File INDEX_BACKUP = new File(CHATLOG_FOLDER, "index.bak");
	private final Deque<Line> messages;
	private final LinkedHashMap<UUID, String> uuidToName;
	private final Summary metadata;
	
	private Session(ArrayDeque<Line> messages, LinkedHashMap<UUID, String> uuidToName, Summary metadata) {
		this.messages = messages;
		this.uuidToName = uuidToName;
		this.metadata = metadata;
	}

	public Deque<Line> getMessages() {
		return this.messages;
	}
	
	public LinkedHashMap<UUID, String> getSendersByUuid() {
		return this.uuidToName;
	}
	
	public Summary getMetadata() {
		return this.metadata;
	}
	
	public static List<Summary> getSessionSummaries() {
		List<Summary> list = new ArrayList<>();
		if(!INDEX.exists()) {
			try {
				INDEX.createNewFile();
			} catch (IOException e) {
				LOGGER.fatal("Failed to create index!");
				e.printStackTrace();
				throw new RuntimeException("Failed to create index!", e);
			}
		}
		
		try(Scanner s = new Scanner(new FileReader(INDEX))){
			while(s.hasNextLine()) {
				try {
					list.add(new Summary(s.nextLine()));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return list;
		}
		
		return list;
	}
	
	private static boolean backupIndex() {
		try {
			Files.copy(INDEX.toPath(), INDEX_BACKUP.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			LOGGER.warn("Unable to backup chat log index!");
			e.printStackTrace();
			return false;
		}
	}
	
	private static boolean restoreIndex() {
		try {
			Files.copy(INDEX_BACKUP.toPath(), INDEX.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			LOGGER.warn("Unable to backup chat log index!");
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * @return The count of sessions being successfully deleted
	 */
	public static int delete(IntSet ids) {
		backupIndex();
		List<Summary> summaries = getSessionSummaries();
		IntSet deleted = new IntOpenHashSet();
		for (int id : ids) {
			if (SessionUtils.id2File(id).delete()) {
				deleted.add(id);
			}
		}

		INDEX.delete();
		try (PrintWriter pw = new PrintWriter(new FileWriter(INDEX))) {
			// This solution seems to be inefficient...
			for (Summary s : summaries) {
				// Avoid detached session summaries & sessions
				if (!deleted.contains(s.id)) {
					s.write(pw);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			restoreIndex();
		}
		
		return deleted.size();
	}
	
	/**
	 * @return The count of sessions being successfully deleted
	 */
	public static boolean updateSummary(Summary summary) {
		backupIndex();
		List<Summary> summaries = getSessionSummaries();
		INDEX.delete();
		try (PrintWriter pw = new PrintWriter(new FileWriter(INDEX))) {
			// This solution seems to be inefficient...
			for (Summary s : summaries) {
				// Avoid detached session summaries & sessions
				if (s.id != summary.id) {
					s.write(pw);
				} else {
					summary.write(pw);
				}
			}
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			restoreIndex();
			return false;
		}
	}
	
	/**
	 * The returned list should be considered immutable.
	 */
	public List<Session> split(List<Line> delimiters) {
		if (delimiters.isEmpty()) {
			return List.of(this);
		}
		
		// Ensure that the remaining lines is included
		delimiters = new ArrayList<>(delimiters);
		delimiters.add(null);
		// XXX Discard original session v.s. 3x memory load
		List<Line> remaining = new LinkedList<>(this.messages);
		List<Session> result = new ArrayList<>();
		Iterator<Line> itr = delimiters.iterator();
		Line nextBorder;
		boolean first = true;
		while (itr.hasNext()) {
			nextBorder = itr.next();
			ArrayDeque<Line> seg = new ArrayDeque<>();
			while (!remaining.isEmpty() && remaining.get(0) != nextBorder) {
				seg.add(remaining.remove(0));
			}
			
			if (seg.isEmpty()) {
				continue;
			}
			
			// XXX: Gap time?
			Summary ori = this.metadata;
			long startTime = first ? ori.startTime : seg.getFirst().time;
			long endTime = itr.hasNext() ? seg.getLast().time : ori.endTime;
			Summary metadata = new Summary(SessionUtils.allocateId(), ori.saveName, startTime, endTime, 
					seg.size(), ori.timeZone, ori.multiplayer, Version.LATEST);
			Session session = new Session(seg, this.uuidToName, metadata);
			result.add(session);
		}
		
		return result;
	}
	
	/**
	 * The returned list should be considered immutable.
	 */
	public Session clip(Line start, Line end) {
		if (start == null && end == null) {
			return this;
		}
		
		if (start == null) {
			return this.split(List.of(end)).get(0);
		}
		

		if (end == null) {
			return this.split(List.of(start)).get(1);
		}
		
		return this.split(List.of(start, end)).get(1);
	}
	
	public SessionRecorder continueRecording() {
		return new SessionRecorder(this); 
	}
	
	/**
	 * Save a manually constructed {@code Session}, usually created by {@link split(List)}.
	 */
	public void save() {
		this.continueRecording().end0();
	}
	
	public static class Line {
		public final UUID sender;
		public final Text message;
		public final long time;
		
		protected Line(UUID sender, Text message, long time) {
			this.sender = sender;
			this.message = message;
			this.time = time;
		}

		public static Proto parse(JsonReader jr) throws IOException {
			jr.beginObject();
			String msgJson = null;
			Integer sender = null;
			long time = 0;
			while(jr.hasNext()) {
				switch (jr.nextName()) {
				case "sender":
					sender = jr.nextInt();
					break;
				case "msgJson":
					msgJson = jr.nextString();
					break;
				case "time":
					time = jr.nextLong();
					break;
				default:
					jr.skipValue();
					break;
				}
			}
			
			if(msgJson == null || sender == null) {
				throw new MalformedJsonException("Incomplete chat line");
			}
			
			jr.endObject();
			return new Proto(sender, Text.Serialization.fromJson(msgJson, DynamicRegistryManager.EMPTY), time);
		}
		
		static Line parseFull(String json) {
			@SuppressWarnings("deprecation")
			JsonObject jo = new JsonParser().parse(json).getAsJsonObject();
			return new Line(UUID.fromString(jo.get("sender").getAsString()), 
					Text.Serialization.fromJson(jo.get("msgJson").getAsString(), DynamicRegistryManager.EMPTY), 
					jo.get("time").getAsLong());
		}

		JsonObject toJson() {
			JsonObject line = new JsonObject();
			line.addProperty("sender", this.sender.toString());
			line.addProperty("msgJson", Text.Serialization.toJsonString(this.message, DynamicRegistryManager.EMPTY));
			line.addProperty("time", this.time);
			return line;
		}
		
		public int getMarkColor() {
			return 0xFF31F38B;
		}
		
		protected final static class Proto {
			public final int senderId;
			public final Text message;
			public final long time;
			
			protected Proto(int senderId, Text message, long time) {
				this.senderId = senderId;
				this.message = message;
				this.time = time;
			}
			
			protected Line toLine(Int2ObjectMap<UUID> uuids) {
				return new Line(uuids.get(this.senderId), this.message, this.time);
			}
		}
	}
	
	public static final class Event extends Line {
		private final int markColor;
		
		public Event(Text title, long time, int markColor) {
			super(Util.NIL_UUID, title, time);
			this.markColor = markColor;
		}
		
		@Override
		public int getMarkColor() {
			return 0xFF000000 | this.markColor;
		}

		static Line parseEvent(String json) {
			@SuppressWarnings("deprecation")
			JsonObject jo = new JsonParser().parse(json).getAsJsonObject();
			return new Event(Text.Serialization.fromJson(jo.get("msgJson").getAsString(), 
							DynamicRegistryManager.EMPTY), 
					jo.get("time").getAsLong(), 
					jo.get("color").getAsInt());
		}
		
		@Override
		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("msgJson", Text.Serialization.toJsonString(this.message, DynamicRegistryManager.EMPTY));
			json.addProperty("time", this.time);
			json.addProperty("color", this.markColor);
			return json;
		}
	}
	
	public static final class Title extends Line {
		private final Type type;

		public Title(Text title, long time, Title.Type type) {
			super(Util.NIL_UUID, title, time);
			this.type = type;
		}
		
		@Override
		public int getMarkColor() {
			return 0xFF000000 | this.type.color;
		}

		static Line parseTitle(String json) {
			@SuppressWarnings("deprecation")
			JsonObject jo = new JsonParser().parse(json).getAsJsonObject();
			return new Title(Text.Serialization.fromJson(jo.get("msgJson").getAsString(), DynamicRegistryManager.EMPTY),
					jo.get("time").getAsLong(), 
					Type.valueOf(jo.get("type").getAsString()));
		}
		
		@Override
		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("msgJson", Text.Serialization.toJsonString(this.message, DynamicRegistryManager.EMPTY));
			json.addProperty("time", this.time);
			json.addProperty("type", this.type.name());
			return json;
		}
		
		public enum Type {
			TITLE(0x000000), 
			SUBTITLE(0x9F9F9F), 
			OVERLAY(0xFFFFFF);
			
			private final int color;

			private Type(int color) {
				this.color = color;
			}
		}
	}
	
	public static class WorldIndicator extends Line {
		private final boolean multiplayer;
		private final String saveName;
		
		protected WorldIndicator(String saveName, boolean multiplayer, long time) {
			super(Util.NIL_UUID, 
					I18N.translateAsText(multiplayer ? "mark.worldindicate" : "mark.worldindicate.mp", saveName), 
					time);
			this.saveName = saveName;
			this.multiplayer = multiplayer;
		}
		
		@Override
		public int getMarkColor() {
			return 0xFFB86960;
		}

		static Line parse(String json) {
			@SuppressWarnings("deprecation")
			JsonObject jo = new JsonParser().parse(json).getAsJsonObject();
			return new WorldIndicator(jo.get("save").getAsString(), 
					jo.get("multiplayer").getAsBoolean(), 
					jo.get("time").getAsLong());
		}

		@Override
		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("time", this.time);
			json.addProperty("save", this.saveName);
			json.addProperty("multiplayer", this.multiplayer);
			return json;
		}
	}
	
	public static final class Summary {
		public final int id;
		public final String saveName;
		public final long startTime;
		public final long endTime;
		public final long size;
		public final TimeZone timeZone;
		public final Version version;
		public final boolean multiplayer;

		Summary(int id, String saveName, long startTime, long endTime, long size, TimeZone timeZone,
				boolean multiplayer, Version version) {
			this.id = id;
			this.saveName = saveName;
			this.startTime = startTime;
			this.endTime = endTime;
			this.size = size;
			this.timeZone = timeZone;
			this.version = version;
			this.multiplayer = multiplayer;
		}
		
		Summary(String idxLine) {
			Iterator<String> itr = StringUtil.unescapeCsvFields(idxLine)
					.stream()
					.map(CharSequence::toString)
					.iterator();
			this.id = Integer.parseInt(itr.next());
			this.saveName = itr.next();	// FIXME We believed no one will use "" in their save names~
			this.startTime = Long.parseLong(itr.next());
			this.endTime = Long.parseLong(itr.next());
			this.size = Integer.parseInt(itr.next());
			if(itr.hasNext()) {
				this.timeZone = TimeZone.getTimeZone(itr.next());
			} else {
				this.timeZone = TimeZone.getDefault();
			}
			
			if (itr.hasNext()) {
				this.version = Version.valueOf(itr.next());
			} else {
				this.version = Version.EARLY_RELEASES;
			}
			
			if (itr.hasNext()) {
				this.multiplayer = Boolean.valueOf(itr.next());
			} else {
				this.multiplayer = false;
			}
		}
		
		protected boolean write() {
			try(PrintWriter pw = new PrintWriter(new FileWriter(INDEX, true))){
				this.write(pw);
				return true;
			} catch (IOException e) {
				LOGGER.error("Failed to write index!");
				e.printStackTrace();
				return false;
			}
		}

		protected void write(PrintWriter pw) throws IOException {
			pw.println(String.format("%d,%s,%d,%d,%d,%s,%s,%s", 
					this.id, StringUtil.escapeCsv(this.saveName), this.startTime, 
					this.endTime, this.size, this.timeZone.getID(), 
					this.version.name(), this.multiplayer));
		}
		
		public final Session load() {
			if(SessionUtils.checkAvailability(this.id)) {
				// Not exist
				return null;
			} else {
				return this.version.load(this);
			}
		}
		
		// Fixes: Filtered session lists are always empty after full-text searches
		@Override
		public int hashCode() {
			return Objects.hash(this.endTime, this.id, this.multiplayer, this.saveName, this.size, 
					this.startTime, this.timeZone, this.version);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			Summary other = (Summary) obj;
			return this.endTime == other.endTime && this.id == other.id && this.multiplayer == other.multiplayer
					&& Objects.equals(this.saveName, other.saveName) && this.size == other.size 
					&& this.startTime == other.startTime && Objects.equals(this.timeZone, other.timeZone) 
					&& this.version == other.version;
		}

		public final String getFormattedStartTime() {
			return Instant.ofEpochMilli(this.startTime)
					.atZone(this.timeZone.toZoneId())
					.format(WorldListWidget.DATE_FORMAT);
		}
	}
	
	public static enum Version {
		EARLY_RELEASES {
			@Override
			protected Session load(Summary summary) {
				File file = SessionUtils.id2File(summary.id);
				List<Line.Proto> protos = null;
				LinkedHashMap<UUID, String> uuidToName = null;
				Int2ObjectMap<UUID> uuids = new Int2ObjectOpenHashMap<>();
				try (InputStreamReader reader = new InputStreamReader(
						new GZIPInputStream(new FileInputStream(file)))) {
					JsonReader jr = new Gson().newJsonReader(reader);
					jr.beginObject();
					while(jr.hasNext()) {
						switch(jr.nextName()) {
						case "messages":
							protos = new ArrayList<>();
							jr.beginArray();
							List<Line.Proto> fuckLambda = protos = new ArrayList<>();
							SessionUtils.wrapTextSerialization(() -> {
								while(jr.hasNext()) {
									fuckLambda.add(Line.parse(jr));
								}
							});
							jr.endArray();
							break;
						case "senders":
							uuidToName = new LinkedHashMap<>();
							jr.beginArray();
							while(jr.hasNext()) {
								jr.beginObject();
								Integer id = null;
								String name = null;
								Long uuid_m = null;
								Long uuid_l = null;
								while(jr.hasNext()) {
									switch(jr.nextName()) {
									case "id":
										id = jr.nextInt();
										break;
									case "name":
										name = jr.nextString();
										break;
									case "uuid_m":
										uuid_m = jr.nextLong();
										break;
									case "uuid_l":
										uuid_l = jr.nextLong();
										break;
									default:
										jr.skipValue();
										break;
									}
								}
								
								if(id == null || name == null || uuid_m == null || uuid_l == null) {
									throw new MalformedJsonException("Incomplete sender info");
								}
								
								UUID uuid = new UUID(uuid_m, uuid_l);
								uuidToName.put(uuid, name);
								uuids.put(id.intValue(), uuid);
								jr.endObject();
							}
							
							jr.endArray();
							break;
						default:
							jr.skipValue();
							//throw new RuntimeException("Unrecognized name: " + name);
						}
					}
					
					jr.endObject();
				} catch (IOException e1) {
					LOGGER.error("Failed to load chat logs!");
					e1.printStackTrace();
					if (Options.allowCorruptedChatlogs) {
						if (protos == null) {
							protos = new ArrayList<>();
						}
						
						if (uuidToName == null) {
							uuidToName = new LinkedHashMap<>();
						}
					} else {
						throw new RuntimeException("Failed to load chat logs!", e1);
					}
				}
				
				if (uuidToName == null || protos == null) {
					LOGGER.error("Incomplete chat log");
					return null;
				}
				
				ArrayDeque<Line> currentChatLogs = protos.stream()
						.map((p) -> p.toLine(uuids))
						.<ArrayDeque<Line>>collect(ArrayDeque<Line>::new, ArrayDeque::add, (r1, r2) -> {});
				return new Session(currentChatLogs, uuidToName, summary);
			}
		}, 
		V_20240826 {
			@Override
			protected Session load(Summary summary) {
				File file = SessionUtils.id2File(summary.id);
				try (InputStreamReader s = new InputStreamReader(new GZIPInputStream(
						new BufferedInputStream(new FileInputStream(file))))) {
					// Skip meta-line
					readLine(s);
					ArrayDeque<Line> lines = new ArrayDeque<>();
					LinkedHashMap<UUID, String> namesByUuid = new LinkedHashMap<>();
					while (true) {
						try {
							String l = readLine(s);
							if (l == null) {
								break;
							}
							
							switch(l.charAt(0)) {
							case 'M':
								SessionUtils.wrapTextSerialization(() -> {
									try {
										lines.add(Line.parseFull(l.substring(1)));
									} catch (Exception e) {
										LOGGER.error("Failed to parse line: {}", l);
										e.printStackTrace();
									}
								});
								break;
							case 'S':
								try(Scanner scannerForLine = new Scanner(l.substring(1))) {
									scannerForLine.useDelimiter(",");
									namesByUuid.put(UUID.fromString(scannerForLine.next()), 
											StringUtil.unescapeCsv(scannerForLine.next()).toString());
								}

								break;
							case 'E':
								SessionUtils.wrapTextSerialization(() -> {
									lines.add(Event.parseEvent(l.substring(1)));
								});
								break;
							case 'W':
								SessionUtils.wrapTextSerialization(() -> {
									lines.add(WorldIndicator.parse(l.substring(1)));
								});
								break;
							case 'T':
								SessionUtils.wrapTextSerialization(() -> {
									lines.add(Title.parseTitle(l.substring(1)));
								});
								break;
							}
						} catch (EOFException | MalformedJsonException | JsonSyntaxException e) {
							e.printStackTrace();
							if (Options.allowCorruptedChatlogs) {
								break;
							} else {
								return null;
							}
						}
					}

					return new Session(lines, namesByUuid, summary);
				} catch (Exception e) {
					LOGGER.error("Failed to load chatlog!");
					e.printStackTrace();
					return null;
				}
			}
			
			@Override
			protected Summary inferMetadata(File unsaved) {
				try (Scanner s = new Scanner(new InputStreamReader(new GZIPInputStream(
						new BufferedInputStream(new FileInputStream(unsaved)))))) {
					long endTime = unsaved.lastModified();
					s.useDelimiter("[,\n]");
					int id = Integer.parseInt(s.next());
					String saveName = StringUtil.unescapeCsv(s.next()).toString();
					long startTime = Long.parseLong(s.next());
					TimeZone timeZone = TimeZone.getTimeZone(s.next());
					s.nextLine();
					int msgCnt = 0;
					while(s.hasNextLine()) {
						String l = s.nextLine();
						if (l.charAt(0) == 'M' || l.charAt(0) == 'E'  || l.charAt(0) == 'T') {
							msgCnt++;
						}
					}
					return new Session.Summary(id, saveName, startTime, endTime, msgCnt, timeZone, 
							false, this);
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}
		}, 
		V_20241011 {
			@Override
			protected Session load(Summary summary) {
				return V_20240826.load(summary);
			}
			
			@Override
			protected Summary inferMetadata(File unsaved) {
				try (InputStreamReader s = new InputStreamReader(new GZIPInputStream(
						new BufferedInputStream(new FileInputStream(unsaved))))) {
					long endTime = unsaved.lastModified();
					String metaLine = readLine(s);
					Iterator<String> itr = StringUtil.unescapeCsvFields(metaLine)
							.stream()
							.map(CharSequence::toString)
							.iterator();
					int id = Integer.parseInt(itr.next());
					String saveName = itr.next();
					long startTime = Long.parseLong(itr.next());
					TimeZone timeZone = TimeZone.getTimeZone(itr.next());
					boolean multiplayer = itr.hasNext() ? Boolean.valueOf(itr.next()) : false;
					int msgCnt = 0;
					while (true) {
						try {
							String l = readLine(s);
							if (l == null) {
								break;
							}
							
							if (l.charAt(0) == 'M' || l.charAt(0) == 'E') {
								msgCnt++;
							}
						} catch (IOException e) {
							break;
						}
					}
					
					return new Session.Summary(id, saveName, startTime, endTime, msgCnt, timeZone, 
							multiplayer, this);
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}
		};
		
		public static final Version LATEST = V_20241011;
		
		protected abstract Session load(Summary summary);

		protected Summary inferMetadata(File unsaved) {
			return null;
		}
		
		// Not using 
		private static String readLine(Reader r) throws IOException {
			StringBuilder line = new StringBuilder();
			try {
				int c = r.read();
				while (c != '\n' && c != '\r' && c != -1) {
					line.append((char) c);
					c = r.read();
				}
				
				// Handle "\r\n" sequence, a little bit nasty solution
				if (c == '\r') {
					r.skip(1);
				}
			} catch (EOFException e) {
			}
			
			return line.length() == 0 ? null : line.toString();
		}
	}
}
