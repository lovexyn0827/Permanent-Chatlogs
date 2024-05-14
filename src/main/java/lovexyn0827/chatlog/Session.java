package lovexyn0827.chatlog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;

import io.netty.util.internal.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lovexyn0827.chatlog.config.Options;
import net.fabricmc.loader.api.FabricLoader;
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
    public static final int FORMAT_VERSION = 0;
    private static final File UNSAVED = new File(CHATLOG_FOLDER, "latest.tmp");
	private static int visibleChatlogLimit = 10000;
	public static Session current;
	private final ArrayDeque<Line> chatLogs;
	private final File chatlogFile;
	private final File tempFile;
	private final PrintWriter tempFileWriter;
	private final LinkedHashMap<UUID, String> uuidToName;
	private ArrayDeque<Line> visibleLineCache;
	private final String saveName;
	private final int id;
	private final long startTime;
	private long endTime;
	public boolean shouldSaveOnDisconnection = false;
	private final TimeZone timeZone;
	private long nextAutoSave = Options.autoSaveIntervalInMs == 0 ? Long.MAX_VALUE : 0;
	private int lastAutoSaveMessageCount = 0;
	private int lastAutoSaveSendererCount = 0;
	
	public Session(String saveName) {
		this.chatLogs = new ArrayDeque<>();
		this.uuidToName = new LinkedHashMap<>();
		this.id = allocateId();
		this.chatlogFile = id2File(this.id);
		this.saveName = saveName;
		this.startTime = System.currentTimeMillis();
		this.endTime = this.startTime;
		this.timeZone = TimeZone.getDefault();
		this.tempFile = UNSAVED;
		PrintWriter temp;
		try {
			temp = new PrintWriter(new BufferedWriter(new FileWriter(this.tempFile)));
			temp.println(String.format("%d,%s,%d,%s", 
					this.id, StringUtil.escapeCsv(this.saveName), this.startTime, this.timeZone.getID()));
		} catch (IOException e) {
			LOGGER.error("Failed to create temp file for chat logs!");
			e.printStackTrace();
			temp = null;
		}
		
		this.tempFileWriter = temp;
	}
	
	private Session(File file, String saveName, long start, long end, TimeZone timeZone) 
			throws MalformedJsonException {
		this.chatlogFile = file;
		this.id = file2Id(file);
		this.timeZone = timeZone;
		this.tempFile = null;
		this.tempFileWriter = null;
		List<Line.Proto> protos = null;
		LinkedHashMap<UUID, String> uuidToName = null;
		Int2ObjectMap<UUID> uuids = new Int2ObjectOpenHashMap<>();
		try (InputStreamReader reader = new InputStreamReader(
				new GZIPInputStream(new FileInputStream(file)))) {
			//System.out.println(String.valueOf(IOUtils.readLines(reader)));
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
			throw new RuntimeException("Failed to load chat logs!", e1);
		}
		
		if(uuidToName == null || protos == null) {
			throw new MalformedJsonException("Incomplete chat log");
		}
		
		this.uuidToName = uuidToName;
		this.chatLogs = protos.stream()
				.map((p) -> p.toLine(uuids))
				.<ArrayDeque<Line>>collect(ArrayDeque<Line>::new, ArrayDeque::add, (r1, r2) -> {});
		this.saveName = saveName;
		this.startTime = start;
		this.endTime = end;
	}
	
	private Session(ArrayDeque<Line> chatLogs, LinkedHashMap<UUID, String> uuidToName, String saveName, int id,
			long startTime, long endTime, TimeZone timeZone) {
		this.chatLogs = chatLogs;
		this.uuidToName = uuidToName;
		this.saveName = saveName;
		this.id = id;
		this.startTime = startTime;
		this.endTime = endTime;
		this.timeZone = timeZone;
		this.chatlogFile = id2File(id);
		this.tempFile = null;
		this.tempFileWriter = null;
	}
	
	private static void wrapTextSerialization(RunnableWithIOException task) throws IOException {
		try {
			PermanentChatLogMod.PERMISSIVE_EVENTS.set(true);
			task.run();
		} finally {
			PermanentChatLogMod.PERMISSIVE_EVENTS.set(false);
		}
	}
	
	public static void tryRestoreUnsaved() {
		if(!UNSAVED.exists() || UNSAVED.length() == 0) {
			return;
		}
		
		MutableBoolean success = new MutableBoolean(false);
		try(Scanner s = new Scanner(new BufferedReader(new FileReader(UNSAVED)))) {
			long endTime = UNSAVED.lastModified();
			s.useDelimiter(",");
			int id = Integer.parseInt(s.next());
			if(checkAvailability(id)) {
				String saveName = StringUtil.unescapeCsv(s.next()).toString();
				long startTime = Long.parseLong(s.next());
				TimeZone timeZone = TimeZone.getTimeZone(s.next());
				s.nextLine();
				ArrayDeque<Line> lines = new ArrayDeque<>();
				LinkedHashMap<UUID, String> namesByUuid = new LinkedHashMap<>();
				while(s.hasNextLine()) {
					String l = s.nextLine();
					switch(l.charAt(0)) {
					case 'M':
						wrapTextSerialization(() -> lines.add(Line.parseFull(l.substring(1))));
						break;
					case 'S':
						try(Scanner scannerForLine = new Scanner(l.substring(1))) {
							scannerForLine.useDelimiter(",");
							namesByUuid.put(UUID.fromString(scannerForLine.next()), 
									StringUtil.unescapeCsv(scannerForLine.next()).toString());
						}
						
						break;
					}
				}
				
				new Session(lines, namesByUuid, saveName, id, startTime, endTime, timeZone).saveAll();
				success.setTrue();
			}
		} catch (Exception e) {
			LOGGER.error("Failed to restore unsaved chatlog!");
			e.printStackTrace();
		}
		
		if(success.getValue()) {
			UNSAVED.delete();
		}
	}

	public void onMessage(UUID sender, Text msg) {
		// TODO Limit memory usage
		this.chatLogs.addLast(new Line(sender, msg, Util.getEpochTimeMs()));
		this.visibleLineCache = null;
		this.uuidToName.computeIfAbsent(sender, (uuid) -> {
			// Naive solution?
			String string = TextVisitFactory.removeFormattingCodes(msg);
			String name = StringUtils.substringBetween(string, "<", ">");
			return name == null ? "[UNSPECIFIED]" : name;
		});
		this.shouldSaveOnDisconnection = true;
		if(System.currentTimeMillis() > this.nextAutoSave) {
			this.autoSave();
		}
	}
	
	public Iterable<Line> getVisibleLines() {
		if(this.chatLogs.size() <= visibleChatlogLimit) {
			return this.chatLogs.clone();
		} else {
			if(this.visibleLineCache != null) {
				return this.visibleLineCache;
			} else {
				Iterator<Line> itr = this.chatLogs.descendingIterator();
				ArrayDeque<Line> result = new ArrayDeque<>();
				for(int i = 0; i < visibleChatlogLimit; i++) {
					result.addFirst(itr.next());
				}
				
				this.visibleLineCache = result;
				return result;
			}
		}
	}
	
	public ArrayDeque<Line> getAllMessages() {
		return this.chatLogs;
	}
	
	public void saveAll() {
		this.endTime = System.currentTimeMillis();
//		if(this.chatLogs.isEmpty()) {
//			return;
//		}
//		
		try (OutputStreamWriter writer = new OutputStreamWriter(
				new GZIPOutputStream(new FileOutputStream(this.chatlogFile)))) {
//			JsonObject root = new JsonObject();
//			JsonArray lineList = new JsonArray();
//			Object2IntMap<UUID> uuids = new Object2IntOpenHashMap<>();
//			for(Line line : this.chatLogs) {
//				lineList.add(line.serialize(uuids));
//			}
//			
//			root.add("messages", lineList);
//			JsonArray uuidList = new JsonArray();
//			for(Entry<UUID> e : uuids.object2IntEntrySet()) {
//				JsonObject item = new JsonObject();
//				item.addProperty("id", e.getIntValue());
//				item.addProperty("name", this.uuidToName.get(e.getKey()));
//				item.addProperty("uuid_m", e.getKey().getMostSignificantBits());
//				item.addProperty("uuid_l", e.getKey().getLeastSignificantBits());
//			}
//			
//			root.add("senders", uuidList);
			Gson gson = new Gson();
			writeIndex(this.chatLogs.size());
			JsonWriter jw = gson.newJsonWriter(writer);
			jw.beginObject();
			jw.name("messages").beginArray();
			Object2IntMap<UUID> uuids = new Object2IntOpenHashMap<>();
			wrapTextSerialization(() -> {
				for(Line line : this.chatLogs) {
					line.serialize(jw, uuids);
				}
			});
			jw.endArray();
			jw.name("senders").beginArray();
			for(Entry<UUID> e : uuids.object2IntEntrySet()) {
				jw.beginObject();
				jw.name("id").value(e.getIntValue());
				jw.name("name").value(this.uuidToName.get(e.getKey()));
				jw.name("uuid_m").value(e.getKey().getMostSignificantBits());
				jw.name("uuid_l").value(e.getKey().getLeastSignificantBits());
				jw.endObject();
			}
			
			jw.endArray();
			jw.endObject();
			if(this.tempFile != null) {
				this.tempFileWriter.close();
				this.tempFile.delete();
			}
			
			this.shouldSaveOnDisconnection = true;
		} catch (IOException e1) {
			LOGGER.error("Failed to save chat logs!");
			e1.printStackTrace();
		}
	}
	
	public void autoSave() {
		if(this.tempFile == null) {
			return;
		}
		
		this.nextAutoSave = System.currentTimeMillis() + Options.autoSaveIntervalInMs;
		Thread saveWorker = new Thread(() -> {
			try {
				ArrayList<Line> allLines = new ArrayList<>(this.chatLogs);
				wrapTextSerialization(() -> {
					for(Line l : allLines.subList(this.lastAutoSaveMessageCount, allLines.size())) {
						this.tempFileWriter.println("M" + l.serializeWithoutIndex());
					}
				});
				this.lastAutoSaveMessageCount = allLines.size();
				ArrayList<Map.Entry<UUID, String>> allSenderers = new ArrayList<>(this.uuidToName.entrySet());
				List<java.util.Map.Entry<UUID, String>> senderersToSave = allSenderers
						.subList(this.lastAutoSaveSendererCount, allSenderers.size());
				for(Map.Entry<UUID, String> e : senderersToSave) {
					this.tempFileWriter.println(String.format("S%s,%s", 
							e.getKey().toString(), 
							StringUtil.escapeCsv(e.getValue())));
				}
				
				this.tempFileWriter.flush();
			} catch (Exception e) {
				LOGGER.error("Failed to perform autosave!");
				e.printStackTrace();
			}
		}, "ChatLog Autosave Worker");
		saveWorker.start();
	}
	
	private void writeIndex(int size) {
		try(PrintWriter pw = new PrintWriter(new FileWriter(INDEX, true))){
			pw.println(String.format("%d,%s,%d,%d,%d,%s", 
					this.id, StringUtil.escapeCsv(this.saveName), this.startTime, 
					this.endTime, size, this.timeZone.getID()));
		} catch (IOException e) {
			LOGGER.error("Failed to write index!");
			e.printStackTrace();
		}
	}
	
	public static List<Summary> getSessionSummaries() {
		List<Summary> list = new ArrayList<>();
		//System.out.println(INDEX.getAbsolutePath());
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

	@Nullable
	public static Session load(Summary s) {
		if(checkAvailability(s.id)) {
			// Not exist
			return null;
		} else {
			try {
				return new Session(id2File(s.id), s.saveName, s.startTime, s.endTime, s.timeZone);
			} catch (MalformedJsonException e) {
				e.printStackTrace();
				return null;
			}
		}
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
	
	private static int file2Id(File f) {
		try (Scanner s = new Scanner(f.getName()).skip("log-")) {
			s.useDelimiter("\\.");
			int id = Integer.parseInt(s.next());
			return id;
		}
	}
	
	public static final class Line {
		public final UUID sender;
		public final Text message;
		public final long time;
		
		protected Line(UUID sender, Text message, long time) {
			this.sender = sender;
			this.message = message;
			this.time = time;
		}

		@SuppressWarnings("deprecation")
		public void serialize(JsonWriter jw, Object2IntMap<UUID> uuids) throws IOException {
			jw.beginObject();
			jw.name("sender").value(uuids.computeIntIfAbsent(this.sender, (k) -> uuids.size()));
			jw.name("msgJson").value(Text.Serializer.toJson(this.message));
			jw.name("time").value(this.time);
			jw.endObject();
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
			return new Proto(sender, Text.Serializer.fromJson(msgJson), time);
		}
		
		public static Line parseFull(String json) {
			@SuppressWarnings("deprecation")
			JsonObject jo = new JsonParser().parse(json).getAsJsonObject();
			return new Line(UUID.fromString(jo.get("sender").getAsString()), 
					Text.Serializer.fromJson(jo.get("msgJson").getAsString()), 
					jo.get("time").getAsLong());
		}

		public JsonObject serializeWithoutIndex() {
			JsonObject line = new JsonObject();
			line.addProperty("sender", this.sender.toString());
			line.addProperty("msgJson", Text.Serializer.toJson(this.message));
			line.addProperty("time", this.time);
			return line;
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
	
	public static final class Summary {
		public final int id;
		public final String saveName;
		public final long startTime;
		public final long endTime;
		public final int size;
		public final TimeZone timeZone;
		
		protected Summary(String idxLine) {
			try(Scanner s = new Scanner(idxLine)){
				s.useDelimiter(",");
				this.id = Integer.parseInt(s.next());
				this.saveName = StringUtil.unescapeCsv(s.next()).toString();
				this.startTime = Long.parseLong(s.next());
				this.endTime = Long.parseLong(s.next());
				this.size = Integer.parseInt(s.next());
				if(s.hasNext()) {
					this.timeZone = TimeZone.getTimeZone(s.next());
				} else {
					this.timeZone = TimeZone.getDefault();
				}
			}
		}
	}
	
	@FunctionalInterface
	private interface RunnableWithIOException {
		void run() throws IOException;
	}
}
