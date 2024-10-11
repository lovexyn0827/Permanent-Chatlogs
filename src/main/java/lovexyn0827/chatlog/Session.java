package lovexyn0827.chatlog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.MalformedJsonException;

import io.netty.util.internal.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lovexyn0827.chatlog.config.Options;
import lovexyn0827.chatlog.i18n.I18N;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Util;

public final class Session {
	private static final Logger LOGGER = LogManager.getLogger("ChatlogSession");
	private static final File CHATLOG_FOLDER = Util.make(() -> {
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
	private static final File INDEX = new File(CHATLOG_FOLDER, "index.ssv");
	private static final File INDEX_BACKUP = new File(CHATLOG_FOLDER, "index.bak");
	private static final File UNSAVED_MARKER = new File(CHATLOG_FOLDER, "unsaved.marker");
	public static Session current;
	private Deque<Line> cachedChatLogs;
	private final LinkedHashMap<UUID, String> uuidToName;
	private final String saveName;
	private final int id;
	private final long startTime;
	private long endTime;
	public boolean shouldSaveOnDisconnection = false;
	private final TimeZone timeZone;
	private volatile boolean finalSaving = false;
	private long messageCount = 0;
	private final Thread autosaveWorker;
	private final Version version;
	public final boolean multiplayer;
	
	// Create a new instance to record a session
	public Session(String saveName, boolean multiplayer) {
		this.cachedChatLogs = new ConcurrentLinkedDeque<>();
		this.uuidToName = new LinkedHashMap<>();
		this.id = allocateId();
		this.saveName = saveName;
		this.startTime = System.currentTimeMillis();
		this.endTime = this.startTime;
		this.timeZone = TimeZone.getDefault();
		this.autosaveWorker = new AutosaveWorker();
		this.autosaveWorker.start();
		this.version = Version.LATEST;
		this.multiplayer = multiplayer;
		markUnsaved(id2File(this.id));
	}
	
	private static void markUnsaved(File unsaved) {
		if (unsaved != null) {
			try (FileWriter fw = new FileWriter(UNSAVED_MARKER)) {
				fw.append(unsaved.getAbsolutePath());
			} catch (IOException e) {
				LOGGER.warn("Unable to create unsaved marker!");
				e.printStackTrace();
			}
		} else {
			UNSAVED_MARKER.delete();
		}
	}
	
	private static File getUnsaved() {
		try (Scanner s = new Scanner(new FileReader(UNSAVED_MARKER))) {
			return new File(s.nextLine());
		} catch (IOException e) {
			return null;
		}
	}

	private Session(ArrayDeque<Line> chatLogs, LinkedHashMap<UUID, String> uuidToName, String saveName, int id,
			long startTime, long endTime, TimeZone timeZone, boolean multiplayer, Version ver) {
		this.cachedChatLogs = chatLogs;
		this.uuidToName = uuidToName;
		this.saveName = saveName;
		this.id = id;
		this.startTime = startTime;
		this.endTime = endTime;
		this.timeZone = timeZone;
		this.autosaveWorker = null;
		this.version = ver;
		this.multiplayer = multiplayer;
	}
	
	private static void wrapTextSerialization(RunnableWithIOException task) throws IOException {
		try {
			PermanentChatLogMod.PERMISSIVE_EVENTS.set(true);
			task.run();
		} finally {
			PermanentChatLogMod.PERMISSIVE_EVENTS.set(false);
		}
	}

	public void onMessage(UUID sender, Text msg) {
		// TODO Limit memory usage
		long timestamp = Util.getEpochTimeMs();
		this.cachedChatLogs.addLast(new Line(sender, msg, timestamp));
		this.uuidToName.computeIfAbsent(sender, (uuid) -> {
			// Naive solution?
			String string = TextVisitFactory.removeFormattingCodes(msg);
			String name = StringUtils.substringBetween(string, "<", ">");
			return name == null ? "[UNSPECIFIED]" : name;
		});
		this.shouldSaveOnDisconnection = true;
		this.messageCount++;
		this.endTime = timestamp;
	}
	
	public Deque<Line> getAllMessages() {
		return this.cachedChatLogs;
	}
	
	public void end() {
		this.finalSaving = true;
	}
	
	/**
	 * Only guaranteed to be accurate when invoked on terminated or deserialized Sessions.
	 */
	private Summary toSummary() {
		return new Summary(this);
	}
	
	private boolean writeSummary() {
		return this.toSummary().write();
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
	
	private static int allocateId() {
		Properties prop = new Properties();
		File optionFile = FabricLoader.getInstance().getConfigDir().resolve("permanent-chat-logs.prop").toFile();
		if(!optionFile.exists()) {
			try {
				optionFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try (FileReader fr = new FileReader(optionFile)) {
			prop.load(fr);
		} catch (IOException e) {
			LOGGER.error("Unable to load configurations.");
			e.printStackTrace();
		}
		
		String nextIdStr = prop.computeIfAbsent("nextId", (k) -> "0").toString();
		int id;
		if(!nextIdStr.matches("\\d+")) {
			LOGGER.error("Invalid next ID: {}", nextIdStr);
			id = findNextAvailableId(0);
		} else {
			id = Integer.parseInt(nextIdStr);
			if(!checkAvailability(id)) {
				LOGGER.error("Occupied ID: {}", nextIdStr);
				id = findNextAvailableId(id);
			}
		}
		
		prop.put("nextId", Integer.toString(id + 1));
		try (FileWriter fw = new FileWriter(optionFile)) {
			prop.store(fw, "");
		} catch (IOException e) {
			LOGGER.error("Unable to save configurations.");
			e.printStackTrace();
		}
		
		return id;
	}

	private static boolean checkAvailability(int id) {
		// TODO Packed chat logs
		return !new File(CHATLOG_FOLDER, String.format("log-%d.json", id)).exists();
	}
	
	/**
	 * @param from Should not be available.
	 */
	private static int findNextAvailableId(int from) {
		while(!checkAvailability(++from));
		return from;
	}
	
	private static File id2File(int id) {
		return new File(CHATLOG_FOLDER, String.format("log-%d.json", id));
	}
	
	public void addEvent(Event e) {
		this.cachedChatLogs.add(e);
		this.messageCount++;
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
			if (id2File(id).delete()) {
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
		List<Line> remaining = new LinkedList<>(this.cachedChatLogs);
		List<Session> result = new ArrayList<>();
		Iterator<Line> itr = delimiters.iterator();
		Line nextBorder;
		while (itr.hasNext()) {
			nextBorder = itr.next();
			ArrayDeque<Line> seg = new ArrayDeque<>();
			while (!remaining.isEmpty() && remaining.get(0) != nextBorder) {
				seg.add(remaining.remove(0));
			}
			
			if (seg.isEmpty()) {
				continue;
			}
			
			Session session = new Session(seg, this.uuidToName, this.saveName, allocateId(), 
					seg.getFirst().time, seg.getLast().time, this.timeZone, this.multiplayer, this.version);
			session.messageCount = seg.size();
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
	
	
	/**
	 * Save a manually constructed {@code Session}, usually created by {@link split(List)}. Shouldn't be invoked
	 * on {@code Session}s created while playing.
	 */
	public void save() {
		this.end();
		try {
			AutosaveWorker saver = new AutosaveWorker(false);
			saver.start();
			saver.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public static void tryRestoreUnsaved() {
		if (current != null) {
			current.end();
			if (current.autosaveWorker != null && current.autosaveWorker.isAlive()) {
				try {
					current.autosaveWorker.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		File unsaved = getUnsaved();
		if (unsaved == null) {
			return;
		}
		
		boolean success = false;
		Version[] loaders = Version.values();
		// Iterate in an reversed order to ensure that newer versions are prioritized.
		Summary s = null;
		for (int i = loaders.length - 1; i >= 0; i--) {
			Version ver = loaders[i];
			if ((s = ver.inferMetadata(unsaved)) != null) {
				break;
			}
		}
		
		if (s != null) {
			success = s.write();
		}
		
		if (!success) {
			SystemToast warning = new SystemToast(new SystemToast.Type(), 
					I18N.translateAsText("gui.restore.failure"), 
					I18N.translateAsText("gui.restore.failure.desc"));
			MinecraftClient.getInstance().getToastManager().add(warning);
		}
		
		markUnsaved(null);
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
			return new Proto(sender, Text.Serialization.fromJson(msgJson), time);
		}
		
		public static Line parseFull(String json) {
			@SuppressWarnings("deprecation")
			JsonObject jo = new JsonParser().parse(json).getAsJsonObject();
			return new Line(UUID.fromString(jo.get("sender").getAsString()), 
					Text.Serialization.fromJson(jo.get("msgJson").getAsString()), 
					jo.get("time").getAsLong());
		}

		public JsonObject toJson() {
			JsonObject line = new JsonObject();
			line.addProperty("sender", this.sender.toString());
			line.addProperty("msgJson", Text.Serialization.toJsonString(this.message));
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

		public static Line parseEvent(String json) {
			@SuppressWarnings("deprecation")
			JsonObject jo = new JsonParser().parse(json).getAsJsonObject();
			return new Event(Text.Serialization.fromJson(jo.get("msgJson").getAsString()), 
					jo.get("time").getAsLong(), 
					jo.get("color").getAsInt());
		}
		
		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("msgJson", Text.Serialization.toJsonString(this.message));
			json.addProperty("time", this.time);
			json.addProperty("color", this.markColor);
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
		
		private Summary(Session s) {
			this.id = s.id;
			this.saveName = s.saveName;
			this.startTime = s.startTime;
			this.endTime = s.endTime;
			this.size = s.messageCount;
			this.timeZone = s.timeZone;
			this.version = s.version;
			this.multiplayer = s.multiplayer;
		}
		

		private Summary(int id, String saveName, long startTime, long endTime, long size, TimeZone timeZone,
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
					Version.LATEST.name(), this.multiplayer));
		}
		
		protected Summary(String idxLine) {
			try(Scanner s = new Scanner(idxLine)){
				s.useDelimiter(",");
				this.id = Integer.parseInt(s.next());
				this.saveName = StringUtil.unescapeCsv(s.next()).toString();	// FIXME: Save name with commas
				this.startTime = Long.parseLong(s.next());
				this.endTime = Long.parseLong(s.next());
				this.size = Integer.parseInt(s.next());
				if(s.hasNext()) {
					this.timeZone = TimeZone.getTimeZone(s.next());
				} else {
					this.timeZone = TimeZone.getDefault();
				}
				
				if (s.hasNext()) {
					this.version = Version.valueOf(s.next());
				} else {
					this.version = Version.EARLY_RELEASES;
				}
				
				if (s.hasNext()) {
					this.multiplayer = Boolean.valueOf(s.next());
				} else {
					this.multiplayer = false;
				}
			}
		}
		
		public final Session load() {
			if(checkAvailability(this.id)) {
				// Not exist
				return null;
			} else {
				return this.version.load(this);
			}
		}
		
		public final String getFormattedStartTime() {
			return Instant.ofEpochMilli(this.startTime)
					.atZone(this.timeZone.toZoneId())
					.format(WorldListWidget.DATE_FORMAT);
		}
	}
	
	@FunctionalInterface
	private interface RunnableWithIOException {
		void run() throws IOException;
	}
	
	public static enum Version {
		EARLY_RELEASES {
			@Override
			protected Session load(Summary summary) {
				File file = id2File(summary.id);
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
							wrapTextSerialization(() -> {
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
				return new Session(currentChatLogs, uuidToName, summary.saveName, summary.id, 
						summary.startTime, summary.endTime, summary.timeZone, summary.multiplayer, 
						Version.EARLY_RELEASES);
			}

			@Override
			protected void serialize(Session session, int id) {
				throw new UnsupportedOperationException("This format is outdated!");
			}
		}, 
		V_20240826 {
			@Override
			protected Session load(Summary summary) {
				File file = id2File(summary.id);
				try (Scanner s = new Scanner(new InputStreamReader(new GZIPInputStream(
						new BufferedInputStream(new FileInputStream(file)))))) {
					long endTime = summary.endTime;
					s.useDelimiter("[,\n]");
					int id = Integer.parseInt(s.next());
					String saveName = StringUtil.unescapeCsv(s.next()).toString();
					long startTime = Long.parseLong(s.next());
					TimeZone timeZone = TimeZone.getTimeZone(s.next());
					s.nextLine();
					ArrayDeque<Line> lines = new ArrayDeque<>();
					LinkedHashMap<UUID, String> namesByUuid = new LinkedHashMap<>();
					while(s.hasNextLine()) {
						try {
							String l = s.nextLine();
							switch(l.charAt(0)) {
							case 'M':
								wrapTextSerialization(() -> {
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
								wrapTextSerialization(() -> {
									lines.add(Event.parseEvent(l.substring(1)));
								});
								break;
							}
						} catch (EOFException | MalformedJsonException e) {
							e.printStackTrace();
							if (!Options.allowCorruptedChatlogs) {
								break;
							} else {
								return null;
							}
						}
					}

					return new Session(lines, namesByUuid, saveName, id, startTime, endTime, timeZone, 
							summary.multiplayer, this);
				} catch (Exception e) {
					LOGGER.error("Failed to load chatlog!");
					e.printStackTrace();
					return null;
				}
			}

			@Override
			protected void serialize(Session session, int id) {
				// Chat logs are saved while playing, so no extra processes are needed.
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
						if (l.charAt(0) == 'M') {
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
			protected void serialize(Session session, int id) {
				V_20240826.serialize(session, id);
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
					boolean multiplayer = Boolean.valueOf(s.next());
					s.nextLine();
					int msgCnt = 0;
					while(s.hasNextLine()) {
						String l = s.nextLine();
						if (l.charAt(0) == 'M') {
							msgCnt++;
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
		protected abstract void serialize(Session session, int id);
		

		protected Summary inferMetadata(File unsaved) {
			return null;
		}
	}
	
	private final class AutosaveWorker extends Thread {
		private PrintWriter chatlogWriter;
		private int lastAutoSaveSendererCount = 0;
		private int finalLoops = 2;
		private final File chatlogFile = id2File(Session.this.id);
		private final boolean updatesEndtimeOnClosing;

		protected AutosaveWorker(boolean updatesEndtimeOnClosing) {
			super("ChatLog Autosave Worker");
			PrintWriter temp;
			try {
				OutputStreamWriter backend = new OutputStreamWriter(new GZIPOutputStream(
						new BufferedOutputStream(new FileOutputStream(this.chatlogFile)), true));
				temp = new PrintWriter(backend);
				temp.println(String.format("%d,%s,%d,%s,%s", 
						Session.this.id, StringUtil.escapeCsv(Session.this.saveName), 
						Session.this.startTime, Session.this.timeZone.getID(), Session.this.multiplayer));
			} catch (IOException e) {
				LOGGER.error("Failed to create temp file for chat logs!");
				e.printStackTrace();
				throw new RuntimeException();
			}
			
			this.chatlogWriter = temp;
			this.updatesEndtimeOnClosing = updatesEndtimeOnClosing;
		}
		
		public AutosaveWorker() {
			this(true);
		}
		
		@Override
		public void run() {
			while (!Session.this.finalSaving || this.finalLoops-- > 0) {
				try {
					wrapTextSerialization(() -> {
						while (!Session.this.cachedChatLogs.isEmpty()) {
							Line l = Session.this.cachedChatLogs.pollFirst();
							this.chatlogWriter.println((l instanceof Event ? "E" : "M") + l.toJson());
						}
					});
					List<Map.Entry<UUID, String>> senders = new ArrayList<>(Session.this.uuidToName.entrySet());
					List<Map.Entry<UUID, String>> sendersToSave = senders
							.subList(this.lastAutoSaveSendererCount, senders.size());
					this.lastAutoSaveSendererCount = senders.size();
					for(Map.Entry<UUID, String> e : sendersToSave) {
						this.chatlogWriter.println(String.format("S%s,%s", 
								e.getKey().toString(), 
								StringUtil.escapeCsv(e.getValue())));
					}
					
					this.chatlogWriter.flush();
					Thread.sleep(Options.realtimeChatlogSaving ? 0 : Options.autoSaveIntervalInMs);
				} catch (Exception e) {
					LOGGER.error("Failed to perform autosave!");
					e.printStackTrace();
				}
			}
			
			this.chatlogWriter.close();
			if (Session.this.messageCount == 0) {
				this.chatlogFile.delete();
				return;
			}
			
			if (updatesEndtimeOnClosing) {
				Session.this.endTime = System.currentTimeMillis();
			}
			
			Session.this.writeSummary();
			Version.LATEST.serialize(Session.this, Session.this.id);
			LOGGER.info("Saved chatlog to: {}", this.chatlogFile);
			markUnsaved(null);
		}
	}
}
