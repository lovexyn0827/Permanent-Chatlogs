package lovexyn0827.chatlog.session;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;

import lovexyn0827.chatlog.PermanentChatLogMod;
import net.fabricmc.loader.api.FabricLoader;

class SessionUtils {
	private static final File NEXT_ID_STORAGE_OLD =FabricLoader.getInstance()
			.getConfigDir()
			.resolve("permanent-chat-logs.prop")
			.toFile();
	private static final File NEXT_ID_STORAGE = new File(Session.CHATLOG_FOLDER, "next_id");
	
	
	static void wrapTextSerialization(RunnableWithIOException task) throws IOException {
		try {
			PermanentChatLogMod.PERMISSIVE_EVENTS.set(true);
			task.run();
		} finally {
			PermanentChatLogMod.PERMISSIVE_EVENTS.set(false);
		}
	}
	
	private static void tryMigrateNextIdStorage() {
		if (NEXT_ID_STORAGE.exists()) {
			return;
		}
		
		int id;
		if (NEXT_ID_STORAGE_OLD.exists()) {
			Properties prop = new Properties();
			try (FileReader fr = new FileReader(NEXT_ID_STORAGE_OLD)) {
				prop.load(fr);
				String nextIdStr = prop.computeIfAbsent("nextId", (k) -> "0").toString();
				if(!nextIdStr.matches("\\d+")) {
					Session.LOGGER.error("Invalid next ID: {}", nextIdStr);
					id = 1048576;	// Minimize the probability of ID conflicts.
				} else {
					id = Integer.parseInt(nextIdStr);
				}
				
			} catch (IOException e) {
				Session.LOGGER.error("Unable read from old ID counter!");
				e.printStackTrace();
				id = 1048576;
			}
		} else {
			id = 0;
		}
		
		try (FileWriter fw = new FileWriter(NEXT_ID_STORAGE)) {
			fw.write(Integer.toString(id));
		} catch (IOException e) {
			Session.LOGGER.error("Unable to create new ID counter!");
			e.printStackTrace();
		}
	}
	
	static int allocateId() {
		tryMigrateNextIdStorage();
		int id;
		try (Scanner s = new Scanner(new FileReader(NEXT_ID_STORAGE))) {
			id = s.nextInt();
		} catch (IOException e) {
			Session.LOGGER.error("Unable read from ID counter!");
			e.printStackTrace();
			id = new Random().nextInt();	// Seldom conflicts, at least
		}
		
		while (!checkAvailability(id)) {
			id++;
		}
		
		try (FileWriter fw = new FileWriter(NEXT_ID_STORAGE)) {
			fw.write(Integer.toString(id + 1));
		} catch (IOException e) {
			Session.LOGGER.error("Unable to save ID counter!");
			e.printStackTrace();
		}
		
		return id;
	}

	static boolean checkAvailability(int id) {
		// TODO Packed chat logs
		return !id2File(id).exists();
	}
	
	@FunctionalInterface
	interface RunnableWithIOException {
		void run() throws IOException;
	}

	static File id2File(int id) {
		return new File(Session.CHATLOG_FOLDER, String.format("log-%d.json", id));
	}
	
	static File lockFileOf(File log) {
		return new File(log.getAbsolutePath() + ".lock");
	}
}
