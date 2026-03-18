package lovexyn0827.chatlog.session;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang3.StringUtils;

import io.netty.util.internal.StringUtil;
import it.unimi.dsi.fastutil.ints.IntSet;
import lovexyn0827.chatlog.config.Options;
import lovexyn0827.chatlog.session.Session.Title;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Util;

public class SessionRecorder {
	private static SessionRecorder current = null;
	private Deque<Session.Line> cachedChatLogs;
	private final LinkedHashMap<UUID, String> uuidToName;
	private final String saveName;
	private final int id;
	private final long startTime;
	private final TimeZone timeZone;
	private long messageCount = 0;
	private final Session.Version version;
	private final boolean multiplayer;
	
	private volatile boolean finalSaving = false;
	private final Thread autosaveWorker;
	
	SessionRecorder(String saveName, boolean multiplayer) {
		this.cachedChatLogs = new ConcurrentLinkedDeque<>();
		this.uuidToName = new LinkedHashMap<>();
		this.id = SessionUtils.allocateId();
		this.saveName = saveName;
		this.startTime = System.currentTimeMillis();
		this.timeZone = TimeZone.getDefault();
		this.version = Session.Version.LATEST;
		this.multiplayer = multiplayer;
		this.autosaveWorker = new AutosaveWorker();
		this.autosaveWorker.start();
		this.writeSummary();
	}
	
	SessionRecorder(Session existing) {
		this.cachedChatLogs = existing.getMessages();
		this.uuidToName = existing.getSendersByUuid();
		Session.Summary metadata = existing.getMetadata();
		this.id = metadata.id;
		this.saveName = metadata.saveName;
		this.startTime = metadata.startTime;
		this.timeZone = metadata.timeZone;
		this.version = metadata.version;
		this.multiplayer = metadata.multiplayer;
		this.autosaveWorker = new AutosaveWorker();
		this.autosaveWorker.start();
	}
	
	public static SessionRecorder start(String saveName, boolean multiplayer) {
		if (Options.newSessionPerMcLaunch) {
			SessionRecorder recorder = current == null ? startAnew(saveName, multiplayer) : current;
			if (Options.gameSessionIndicator) {
				recorder.addWorldIndicator(saveName, multiplayer);
			}
			
			return recorder;
		} else {
			return startAnew(saveName, multiplayer);
		}
	}

	private static SessionRecorder startAnew(String saveName, boolean multiplayer) {
		SessionRecorder recorder = new SessionRecorder(saveName, multiplayer);
		UnsavedChatlogRecovery.markUnsaved(SessionUtils.id2File(recorder.id));
		current = recorder;
		return recorder;
	}

	public static SessionRecorder current() {
		return current;
	}
	
	/**
	 * Called when the player leaves a world, or when the client exits
	 * @param clientExiting {@code true} if the client is exiting
	 */
	public static void end(boolean clientExiting) {
		if (current == null) {
			return;
		}
		
		current.updateSummary();
		if (!Options.newSessionPerMcLaunch || clientExiting) {
			current.end0();
			current = null;
		}
	}

	public void onMessage(UUID sender, Text msg) {
		long timestamp = Util.getEpochTimeMs();
		this.cachedChatLogs.addLast(new Session.Line(sender, msg, timestamp));
		this.uuidToName.computeIfAbsent(sender, (uuid) -> {
			// Naive solution?
			String string = TextVisitFactory.removeFormattingCodes(msg);
			String name = StringUtils.substringBetween(string, "<", ">");
			return name == null ? "[UNSPECIFIED]" : name;
		});
		this.messageCount++;
	}
	
	public void addEvent(Session.Event e) {
		this.cachedChatLogs.add(e);
		this.messageCount++;
	}

	public void addOverlayMessage(Text message, boolean tinted, long time) {
		this.cachedChatLogs.add(new Title(message, time, Title.Type.OVERLAY));
		this.messageCount++;
	}

	public void addTitle(Text message, long time) {
		this.cachedChatLogs.add(new Title(message, time, Title.Type.TITLE));
		this.messageCount++;
	}

	public void addSubtitle(Text message, long time) {
		this.cachedChatLogs.add(new Title(message, time, Title.Type.SUBTITLE));
		this.messageCount++;
	}

	public int getId() {
		return this.id;
	}
	
	private void addWorldIndicator(String saveName, boolean multiplayer) {
		this.cachedChatLogs.add(new Session.WorldIndicator(saveName, multiplayer, Util.getEpochTimeMs()));
	}
	
	void end0() {
		this.finalSaving = true;
		try {
			this.autosaveWorker.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Only guaranteed to be accurate when invoked on terminated or deserialized Sessions.
	 */
	private Session.Summary toSummary() {
		return new Session.Summary(this.id, this.saveName, this.startTime, Util.getEpochTimeMs(), this.messageCount, 
				this.timeZone, this.multiplayer, this.version);
	}
	
	private boolean writeSummary() {
		return this.toSummary().write();
	}
	
	private void updateSummary() {
		Session.updateSummary(this.toSummary());
	}
	
	private final class AutosaveWorker extends Thread {
		private PrintWriter chatlogWriter;
		private int lastAutoSaveSendererCount = 0;
		private int finalLoops = 2;
		private final File chatlogFile = SessionUtils.id2File(SessionRecorder.this.id);
		private final File lockFile;
		private FileOutputStream lockHolder;

		protected AutosaveWorker() {
			super("ChatLog Autosave Worker");
			PrintWriter backend;
			try {
				FileOutputStream fos = new FileOutputStream(this.chatlogFile);
				backend = new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(
						new BufferedOutputStream(fos), true)));
				backend.println(String.format("%d,%s,%d,%s,%s", 
						SessionRecorder.this.id, StringUtil.escapeCsv(SessionRecorder.this.saveName), 
						SessionRecorder.this.startTime, SessionRecorder.this.timeZone.getID(), 
						SessionRecorder.this.multiplayer));
				backend.flush();
			} catch (IOException e) {
				Session.LOGGER.error("Failed to create temp file for chat logs!");
				e.printStackTrace();
				throw new RuntimeException();
			}
			
			this.chatlogWriter = backend;
			this.lockFile = SessionUtils.lockFileOf(this.chatlogFile);
			try {
				this.lockHolder = new FileOutputStream(this.lockFile);
				if (this.lockHolder.getChannel().tryLock() == null) {
					Session.LOGGER.error("Failed to lock chatlog file: {}", this.chatlogFile);
				}
			} catch (IOException e) {
				Session.LOGGER.error("Failed to lock chatlog file: {}", this.chatlogFile);
				e.printStackTrace();
				this.lockHolder = null;
			}
		}
		
		private static char getLineTypeMarker(Session.Line l) {
			if (l instanceof Session.Event) {
				return 'E';
			} else if (l instanceof Session.WorldIndicator) {
				return 'W';
			} else if (l instanceof Session.Title) {
				return 'T';
			} else {
				return 'M';
			}
		}
		
		@Override
		public void run() {
			while (!SessionRecorder.this.finalSaving || this.finalLoops-- > 0) {
				try {
					List<Map.Entry<UUID, String>> senders = 
							new ArrayList<>(SessionRecorder.this.uuidToName.entrySet());
					List<Map.Entry<UUID, String>> sendersToSave = 
							senders.subList(this.lastAutoSaveSendererCount, senders.size());
					this.lastAutoSaveSendererCount = senders.size();
					for(Map.Entry<UUID, String> e : sendersToSave) {
						this.chatlogWriter.print(String.format("S%s,%s\n", 
								e.getKey().toString(), 
								StringUtil.escapeCsv(e.getValue())));
					}

					SessionUtils.wrapTextSerialization(() -> {
						while (!SessionRecorder.this.cachedChatLogs.isEmpty()) {
							Session.Line l = SessionRecorder.this.cachedChatLogs.pollFirst();
							this.chatlogWriter.print(getLineTypeMarker(l));
							// Always use '\n' as line terminator
							this.chatlogWriter.print(l.toJson());
							this.chatlogWriter.append('\n');
						}
					});
					this.chatlogWriter.flush();
					Thread.sleep(Options.realtimeChatlogSaving ? 0 : Options.autoSaveIntervalInMs);
				} catch (Exception e) {
					Session.LOGGER.error("Failed to perform autosave!");
					e.printStackTrace();
				}
			}

			this.chatlogWriter.close();
			if (SessionRecorder.this.messageCount == 0) {
				Session.delete(IntSet.of(SessionRecorder.this.id));
				UnsavedChatlogRecovery.unmarkUnsaved(this.chatlogFile);
				return;
			}
			
			if (this.lockHolder != null) {
				try {
					this.lockHolder.close();
					this.lockFile.delete();
				} catch (IOException e) {
					Session.LOGGER.error("Failed to unlock chatlog file: {}", this.chatlogFile);
					e.printStackTrace();
				}
			}
			
			Session.LOGGER.info("Saved chatlog to: {}", this.chatlogFile);
			UnsavedChatlogRecovery.unmarkUnsaved(this.chatlogFile);
		}
	}
}
