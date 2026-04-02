package lovexyn0827.chatlog.session;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.LockSupport;

import lovexyn0827.chatlog.config.Options;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;

public class UnsavedChatlogRecovery {
	private static final File UNSAVED_MARKER = new File(Session.CHATLOG_FOLDER, "unsaved.marker");

	static void markUnsaved(File unsaved) {
		waitForFile(UNSAVED_MARKER);
		try (FileWriter fw = new FileWriter(UNSAVED_MARKER, true)) {
			fw.append(unsaved.getAbsolutePath());
			fw.append('\n');
		} catch (IOException e) {
			Session.LOGGER.warn("Unable to create unsaved marker!");
			e.printStackTrace();
		}
	}
	
	static void unmarkUnsaved(File unsaved) {
		if (unsaved != null) {
			waitForFile(UNSAVED_MARKER);
			List<String> filtered = new ArrayList<>();
			String unsavedFilePath = unsaved.getAbsolutePath();
			try (Scanner s = new Scanner(new FileReader(UNSAVED_MARKER))) {
				while (s.hasNextLine()) {
					String line = s.nextLine();
					if (!line.equals(unsavedFilePath) && !line.isEmpty()) {
						filtered.add(line);
					}
				}
			} catch (IOException e) {
				Session.LOGGER.warn("Unable to load unsaved marker!");
				e.printStackTrace();
			}

			waitForFile(UNSAVED_MARKER);
			try (FileWriter fw = new FileWriter(UNSAVED_MARKER)) {
				for (String l : filtered) {
					fw.append(l);
					fw.append('\n');
				}
			} catch (IOException e) {
				Session.LOGGER.warn("Unable to save unsaved marker!");
				e.printStackTrace();
			}
		} else {
			UNSAVED_MARKER.delete();
		}
	}

	private static boolean isFileInUse(File f) {
		if (!f.exists()) {
			return false;
		}
		
		try (RandomAccessFile raf = new RandomAccessFile(f, "rw"); 
				FileChannel channel = raf.getChannel()) {
			FileLock lock = channel.tryLock();
			if (lock == null) {
				return true;
			} else {
				lock.release();
				return false;
			}
		} catch (OverlappingFileLockException e) {
			return true;
		} catch (IOException e1) {
			return true;
		}
	}
	
	private static void waitForFile(File f) {
		while (isFileInUse(f)) {
			LockSupport.parkNanos(1000000);
		}
	}

	private static List<File> getUnsaved() {
		List<File> unsaved = new ArrayList<>();
		waitForFile(UNSAVED_MARKER);
		try (Scanner s = new Scanner(new FileReader(UNSAVED_MARKER))) {
			while (s.hasNextLine()) {
				unsaved.add(new File(s.nextLine()));
			}
		} catch (IOException e) {
			return Collections.emptyList();
		}
		
		return unsaved;
	}
	
	public static void tryRestoreUnsaved() {
		if (Options.newSessionPerMcLaunch && SessionRecorder.current() != null) {
			return;
		}
		
		getUnsaved().forEach(UnsavedChatlogRecovery::tryRestoreUnsaved);
	}

	private static void tryRestoreUnsaved(File unsaved) {
		if (unsaved == null || isFileInUse(SessionUtils.lockFileOf(unsaved)) || !unsaved.exists()) {
			return;
		}
		
		boolean success = false;
		Session.Version[] loaders = Session.Version.values();
		// Iterate in an reversed order to ensure that newer versions are prioritized.
		Session.Summary summary = null;
		for (int i = loaders.length - 1; i >= 0; i--) {
			Session.Version ver = loaders[i];
			if ((summary = ver.inferMetadata(unsaved)) != null) {
				break;
			}
		}
		
		if (summary == null) {
			return;
		}
		
		if (summary.size == 0) {
			unmarkUnsaved(unsaved);
			return;
		}
		
		int id = summary.id;
		if (Session.getSessionSummaries().stream().anyMatch((s) -> s.id == id)) {
			success = Session.updateSummary(summary);
		} else {
			success = summary.write();
		}
		
		if (!success) {
			SystemToast warning = new SystemToast(new SystemToast.Type(), 
					I18N.translateAsText("gui.restore.failure"), 
					I18N.translateAsText("gui.restore.failure.desc"));
			MinecraftClient.getInstance().getToastManager().add(warning);
		}
		
		SessionUtils.lockFileOf(unsaved).delete();
		unmarkUnsaved(unsaved);
	}
}
