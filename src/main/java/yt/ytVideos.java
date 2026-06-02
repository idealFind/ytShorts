package yt;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class ytVideos {

	private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter.ofPattern("HH:mm | dd MMM yyyy");

	private static final List<String> PROJECTS = Arrays.asList("project1", "project2", "project3", "project4",
			"project5");

	// ================= CONFIG =================
	private static final String IMAGE_PREFIX = "img";

	// private static final int MAX_CHARS_PER_LINE = 55;
	private static final int FONT_SIZE = 32; // 30
	private static final int LINE_HEIGHT = 40; // 40
	public static String pageTitle;
	public static Page page;
	public static String safeTitle;

	private static final Path MUSIC_DIR = Paths.get("music");

	private static final Random RANDOM = new Random();
	private static final String APPLICATION_NAME = "YouTubeUploaderClient";
	private static final Path REPORT_DIR = Paths.get("reports");

	private static final Path REPORT_CSV = REPORT_DIR.resolve("upload_report.csv");

	private static final Path REPORT_HTML = REPORT_DIR.resolve("dashboard.html");

	private static final Path LOG_DIR = REPORT_DIR.resolve("logs");

	private static final Path STATE_DIR = Paths.get("state");

	private static final Path PROJECT_INDEX_FILE = STATE_DIR.resolve("project_index.txt");

	private static final Path QUOTA_CACHE_FILE = STATE_DIR.resolve("quota_exceeded.txt");

	// ==========================================

	static class SceneData {

		Path image;
		Path audio;
		Path segment;

		double duration;

		String transition;

		String motionPreset;

		public SceneData(Path image, Path audio, Path segment, double duration, String transition,
				String motionPreset) {

			this.image = image;
			this.audio = audio;
			this.segment = segment;
			this.duration = duration;
			this.transition = transition;
			this.motionPreset = motionPreset;
		}
	}

	public static void main(String[] args) throws Exception {

		List<String> urls = Files.readAllLines(Paths.get("urls.txt"), StandardCharsets.UTF_8).stream().map(String::trim)
				.filter(s -> !s.isBlank()).collect(Collectors.toList());

		// for (String url : urls) {
		for (String rawUrl : urls) {

			String url = rawUrl.trim();

			// SKIP INVALID URLS
			if (!url.startsWith("http://") && !url.startsWith("https://")) {

				System.out.println("\n⚠ INVALID URL SKIPPED: " + url);

				appendReport("SKIPPED", "INVALID_URL", url, "", "", "Invalid URL in urls.txt");

				continue;
			}

			System.out.println("=================================");
			System.out.println(" PROCESSING URL ");
			System.out.println(url);
			System.out.println("=================================");

			List<String> captions = new ArrayList<>();
			String safeTitle;
			// String pageTitle = null;

			try (Playwright pw = Playwright.create()) {

				Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

				BrowserContext context = browser.newContext();
				page = context.newPage();
				page.setDefaultTimeout(60000);
				page.setDefaultNavigationTimeout(60000);

				// page.navigate(url, new
				// Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				try {

					page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

				} catch (Exception ex) {

					System.out.println("⚠ FAILED TO OPEN URL: " + url);

					appendReport("FAILED", "NAVIGATION", url, "", "", ex.getMessage());

					continue;
				}
				page.waitForTimeout(4000);

				// pageTitle = page.locator("(//section//b)[1]").innerText();
				// pageTitle = page.locator("(//h1)[2]").innerText().trim();
				getPageTitle();
				pageTitle = normalizePossessive(pageTitle);
				safeTitle = sanitizeForFolderName(pageTitle);
				System.out.println("safeTitle --> " + safeTitle);

				//
				// ROOT VIDEOS FOLDER
				//
				Path videosRoot = Paths.get("videos");

				//
				// TITLE FOLDER INSIDE videos/
				//
				Path baseDir = videosRoot.resolve(safeTitle);
				System.out.println("VIDEO DIRECTORY: " + baseDir.toAbsolutePath());

				//
				// SUBFOLDERS
				//
				Path downloadDir = baseDir.resolve("downloaded_images");

				Path finalDir = baseDir.resolve("final_images_with_text");

				Path segmentsDir = baseDir.resolve("video_segments");

				//
				// CREATE DIRECTORIES
				//
				Files.createDirectories(videosRoot);

				Files.createDirectories(baseDir);

				Files.createDirectories(downloadDir);

				Files.createDirectories(finalDir);

				Files.createDirectories(segmentsDir);

				// List<Locator> images =
				// page.locator("div.entertainment-background").first().locator("img[alt]").all();

				List<Locator> cards = page.locator("div.photos_cardposition__71WWM").all();

				int index = 1;

				for (Locator card : cards) {

					try {
						Locator img = card.locator("div.main-img img");

						String src = img.getAttribute("src");

						if (card.innerText().contains("ADVERTISEMENT"))
							continue;

						if (src == null)
							continue;

						String caption;

						// ✅ FIRST IMAGE → TAKE FULL SPAN TEXT (single line)
						if (index == 1) {
							Locator spanNode = card.locator("div.photos_photogallytitlepos__PgJ0k span");
							caption = spanNode.innerText().trim();
						} else {
							Locator firstPara = card.locator("div.photos_photogallytitlepos__PgJ0k span p").first();
							caption = firstPara.innerText().trim();
						}

						// ✅ NEW LINE (IMPORTANT)
						caption = caption.replaceAll("\\s+", " ").trim();
						caption = getFirstSentence(caption);

						// ✅ REMOVE PHOTO CREDITS
						caption = caption.replaceAll("\\(Pics?/.*?\\)", "");
						caption = caption.replaceAll("\\(Images?/.*?\\)", "");
						caption = caption.replaceAll("\\(Photo[s]?/.*?\\)", "");
						caption = caption.replaceAll("\\(Pic credit:.*?\\)", "");

						caption = normalizePossessive(caption);

						caption = caption.replaceAll("\\s+", " ").trim();

						System.out.println("[" + index + "] CAPTION: " + caption);

						Path out = downloadDir.resolve(String.format("%s%03d.jpg", IMAGE_PREFIX, index));
						try {
							downloadImageWithPlaywright(page, src, out);

							if (Files.exists(out) && Files.size(out) > 1000) {
								captions.add(caption);
								index++;
							} else {
								System.out.println("⚠ Image failed, skipping caption");
							}

						} catch (Exception e) {
							System.out.println("⚠ Download failed, skipping");
						}

					} catch (Exception e) {
						System.out.println("⚠️ Skipping broken card...");
					}
				}

//				int index = 1;
//				for (Locator card : cards) {
//
//					try {
//						Locator img = card.locator("div.main-img img");
//						Locator captionNode = card.locator("div.photos_photogallytitlepos__PgJ0k span p").first();
//
//						String src = img.getAttribute("src");
//						String caption = captionNode.innerText().trim();
//
//						if (card.innerText().contains("ADVERTISEMENT"))
//							continue;
//
//						if (src == null || caption.isBlank())
//							continue;
//
//						caption = normalizePossessive(caption);
//						caption = caption.replaceAll("\\s+", " ").trim();
//
//						System.out.println("[" + index + "] CAPTION: " + caption);
//
//						Path out = downloadDir.resolve(String.format("%s%03d.jpg", IMAGE_PREFIX, index));
//						downloadImageWithPlaywright(page, src, out);
//
//						captions.add(caption);
//						index++;
//
//					} catch (Exception e) {
//						System.out.println("⚠️ Skipping broken card...");
//					}
//				}

				createTextFile(baseDir, pageTitle, captions);
				browser.close();

				// ================= TEXT OVERLAY (NO RESIZE) =================

				List<Path> downloadedImages = Files.list(downloadDir).sorted(Comparator.comparing(Path::toString))
						.collect(Collectors.toList());

				// ================= CONVERT TO JPG =================

				List<Path> jpgImages = new ArrayList<>();

				int convertIndex = 1;

				for (Path img : downloadedImages) {

					Path jpgOut = finalDir.resolve("temp_" + String.format("%03d", convertIndex++) + ".jpg");

					runFFmpeg(List.of("ffmpeg", "-y", "-i", img.toAbsolutePath().toString(),
							jpgOut.toAbsolutePath().toString()), "Convert JPG");

					jpgImages.add(jpgOut);
				}

				// ================= ADD TEXT =================

				int videoIndex = 1;

				int safeSize = Math.min(jpgImages.size(), captions.size());

				for (int i = 0; i < safeSize; i++) {

					Path input = jpgImages.get(i);

					Path output = finalDir.resolve(String.format("%s%03d.jpg", IMAGE_PREFIX, videoIndex));

					if (addTextJava2D(input, output, captions.get(i))) {
						videoIndex++;
					}
				}

				// ================= VIDEO + TTS =================
				List<Path> finalImages = Files.list(finalDir).sorted().collect(Collectors.toList());

				Path concatListFile = baseDir.resolve("concat_list.txt");

				try (BufferedWriter writer = Files.newBufferedWriter(concatListFile)) {

					int segmentSafeSize = Math.min(finalImages.size(), captions.size());

					for (int i = 0; i < segmentSafeSize; i++) {

						Path image = finalImages.get(i);
						String text = captions.get(i);

						Path ttsAudio = segmentsDir.resolve(String.format("audio_%03d.mp3", i));
						Path videoSegment = segmentsDir.resolve(String.format("seg_%03d.mp4", i));

						System.out.println("\nSEGMENT " + i + " of " + segmentSafeSize);
						System.out.println("TEXT: " + text);

						boolean ttsSuccess = generateTTS(text, ttsAudio);

						if (!ttsSuccess || !Files.exists(ttsAudio)) {
							System.out.println("⚠ TTS FAILED → using silent audio");
							createSilentAudio(ttsAudio);
						}

						System.out.println("Audio Exists: " + Files.exists(ttsAudio));
						System.out.println("Audio Size: " + Files.size(ttsAudio));

						double audioDuration = Math.max(2.5, getAudioDuration(ttsAudio));

						runFFmpeg(List.of("ffmpeg", "-y",

								"-loop", "1", "-i", image.toString(),

								"-i", ttsAudio.toString(),

								"-t", String.valueOf(audioDuration),

								"-filter_complex",

								// ===== BLURRED BACKGROUND =====
								"[0:v]scale=1920:1080:force_original_aspect_ratio=increase," + "crop=1920:1080,"
										+ "gblur=sigma=25[bg];"

										// ===== FOREGROUND IMAGE =====
										+ "[0:v]scale=1920:1080:force_original_aspect_ratio=decrease[fg];"

										// ===== CENTER IMAGE =====
										+ "[bg][fg]overlay=(W-w)/2:(H-h)/2",

								"-c:v", "libx264",

								// medium for slightly medium quality
								"-preset", "medium", "-crf", "22",

								// slow for high quality
								// "-preset", "slow",
								// "-crf", "18",

								"-pix_fmt", "yuv420p",

								"-r", "24",

								"-c:a", "aac",

								"-shortest",

								videoSegment.toString()

						), "Cinematic Segment");

						// ✅ THEN CHECK
						if (!Files.exists(videoSegment)) {
							System.out.println("❌ SEGMENT FAILED: " + videoSegment);
							continue;
						}

						// ✅ SAFE PATH FIX
						String safePath = videoSegment.toAbsolutePath().toString().replace("\\", "/").replace("'", "");

						writer.write("file '" + safePath + "'\n");
					}
				}

				System.out.println("\n=== CONCAT FILE CONTENT ===");
				Files.lines(concatListFile).forEach(System.out::println);

				System.out.println("\n=== SEGMENT FILE CHECK ===");
				Files.list(segmentsDir).forEach(System.out::println);

				Path rawVideo = baseDir.resolve("raw_video.mp4");

				runFFmpeg(List.of("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i",
						concatListFile.toAbsolutePath().toString(),

						"-c", "copy",

						rawVideo.toAbsolutePath().toString()), "Concat");

				Path music = getRandomMusicFile();

				Path finalVideo = baseDir.resolve(safeTitle + ".mp4");

				if (music != null) {

					runFFmpeg(List.of("ffmpeg", "-y",

							"-i", rawVideo.toAbsolutePath().toString(),

							"-stream_loop", "2", "-i", music.toAbsolutePath().toString(),

							"-filter_complex",
							"[0:a]volume=1.0[a0];" + "[1:a]volume=0.05[a1];"
									+ "[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[a]",

							"-map", "0:v", "-map", "[a]",

							"-c:v", "copy", "-c:a", "aac",

							"-shortest",

							finalVideo.toString()

					), "Final Mix");

				} else {

					// ✅ No music → just rename/copy raw video
					Files.copy(rawVideo, finalVideo, StandardCopyOption.REPLACE_EXISTING);
				}

				// ================= STEP 5: YOUTUBE METADATA =================

				// Combine all captions into one string
				String allCaptions = String.join(". ", captions);

				// Generate viral title
				String viralTitle = generateViralTitle(pageTitle);

				// Generate tags
				List<String> tags = generateTags(viralTitle);

				// Generate hashtags
				String hashtags = generateHashtags(tags);

				// Generate description
				String description = generateDescription(viralTitle, allCaptions, hashtags);

				// Debug print (VERY IMPORTANT)
				System.out.println("\n===== YOUTUBE SEO DATA =====");
				System.out.println("TITLE: " + viralTitle);
				System.out.println("TAGS: " + tags);
				System.out.println("HASHTAGS: " + hashtags);
				System.out.println("DESCRIPTION:\n" + description);

				// 👉 CALL UPLOAD HERE
				// uploadToYouTube(baseDir.resolve(safeTitle + ".mp4").toFile(), viralTitle,
				// description, tags);
				uploadToYouTube(baseDir.resolve(safeTitle + ".mp4").toFile(), viralTitle, description, tags, url);
			}
		}
	}

	private static List<String> generateTags(String title) {

		List<String> tags = new ArrayList<>();

		if (title == null || title.isBlank()) {
			return tags;
		}

		String[] words = title.split("\\s+");

		Set<String> stopWords = Set.of("this", "that", "with", "from", "have", "were", "they", "about", "there",
				"their", "what", "when", "your", "will", "into", "than");

		for (String word : words) {

			word = word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

			if (word.length() < 4) {
				continue;
			}

			if (!stopWords.contains(word)) {
				tags.add(word);
			}
		}

		// TRENDING BOOST TAGS
		tags.addAll(List.of("viral", "trending", "youtube shorts", "breaking news", "entertainment", "celebrity news",
				"india news", "cricket", "bollywood", "viral video", "shorts"));

		return tags.stream().distinct().limit(50).collect(Collectors.toList());
	}

	private static void getPageTitle() {
		try {

			Locator h1Second = page.locator("(//h1)[2]");

			if (h1Second.count() > 0) {

				pageTitle = h1Second.first().innerText().trim();

			} else {

				pageTitle = page.locator("(//h1)[1]").first().innerText().trim();
			}

			// BAD TITLES FILTER
			if (pageTitle == null || pageTitle.isBlank() || pageTitle.toLowerCase().contains("opt out")
					|| pageTitle.toLowerCase().contains("privacy") || pageTitle.toLowerCase().contains("cookie")) {

				pageTitle = "shorts_" + System.currentTimeMillis();
			}

			safeTitle = sanitizeForFolderName(pageTitle);

			System.out.println("PAGE TITLE: " + pageTitle);
			System.out.println("SAFE TITLE: " + safeTitle);

		} catch (Exception e) {

			pageTitle = "shorts_" + System.currentTimeMillis();

			safeTitle = sanitizeForFolderName(pageTitle);
		}
	}

	private static String addSpeechPauses(String text) {
		if (text == null)
			return "";

		return text.replaceAll("\\.\\s*", ". <break time=\"600ms\"/> ")
				.replaceAll("\\?\\s*", "? <break time=\"600ms\"/> ").replaceAll("!\\s*", "! <break time=\"600ms\"/> ");
	}

	private static double getAudioDuration(Path audioFile) {

		try {

			ProcessBuilder pb = new ProcessBuilder("ffprobe", "-i", audioFile.toString(), "-show_entries",
					"format=duration", "-v", "quiet", "-of", "csv=p=0");

			pb.redirectErrorStream(true);

			Process p = pb.start();

			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));

			String line = br.readLine();

			p.waitFor();

			if (line != null && !line.isBlank()) {

				double d = Double.parseDouble(line.trim());

				// SAFETY LIMITS
				if (d > 0 && d < 60) {
					return d;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return 4.0;
	}

	public static Credential authorize(String projectName) throws Exception {

		Path credentialPath = Paths.get("credentials", projectName, "client_secret.json");

		Path tokenPath = Paths.get("credentials", projectName, "tokens");

		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(),
				new InputStreamReader(new FileInputStream(credentialPath.toFile())));

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), clientSecrets,
				Collections.singletonList("https://www.googleapis.com/auth/youtube.upload")).setAccessType("offline")
				.setDataStoreFactory(new FileDataStoreFactory(tokenPath.toFile())).build();

		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

		return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
	}

	private static String getFirstSentence(String text) {
		if (text == null || text.isBlank())
			return text;

		// Split on sentence-ending punctuation
		String[] parts = text.split("(?<=[.!?])\\s+");

		return parts.length > 0 ? parts[0].trim() : text.trim();
	}

	// private static void uploadToYouTube(File videoFile, String title, String
	// description, List<String> tags) {
	private static void uploadToYouTube(File videoFile, String title, String description, List<String> tags,
			String sourceUrl) {
		try {

			Files.createDirectories(REPORT_DIR);
			Files.createDirectories(LOG_DIR);
			Files.createDirectories(STATE_DIR);

			List<String> projects = getAvailableProjects();

			if (projects.isEmpty()) {

				throw new RuntimeException("No projects found");
			}

			int startIndex = getNextProjectIndex(projects.size());

			Collections.rotate(projects, -startIndex);

			System.out.println("\nSTARTING PROJECT: " + projects.get(0));

			for (String project : projects) {

				if (isProjectQuotaExceeded(project)) {

					System.out.println("⚠ SKIPPING QUOTA EXHAUSTED: " + project);

					continue;
				}

				try {

					System.out.println("\n=================================");

					System.out.println("TRYING PROJECT: " + project);

					System.out.println("=================================");

					Credential credential = authorize(project);

					YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(),
							JacksonFactory.getDefaultInstance(), credential).setApplicationName(APPLICATION_NAME)
							.build();

					Video video = new Video();

					VideoSnippet snippet = new VideoSnippet();

					snippet.setTitle(title);

					snippet.setDescription(description);

					snippet.setTags(tags);

					video.setSnippet(snippet);

					VideoStatus status = new VideoStatus();

					status.setPrivacyStatus("public");

					video.setStatus(status);

					InputStreamContent mediaContent = new InputStreamContent("video/*", new FileInputStream(videoFile));

					YouTube.Videos.Insert request = youtube.videos().insert("snippet,status", video, mediaContent);

					request.getMediaHttpUploader().setDirectUploadEnabled(false);

					Video response = request.execute();

					String videoUrl = "https://youtube.com/watch?v=" + response.getId();

					System.out.println("✅ UPLOADED SUCCESSFULLY");

					System.out.println(videoUrl);

					appendReport("SUCCESS", project, sourceUrl, response.getId(), videoUrl, "");

					generateHtmlDashboard();

					return;

				} catch (GoogleJsonResponseException e) {

					String reason = "UNKNOWN";

					try {

						reason = e.getDetails().getErrors().get(0).getReason();

					} catch (Exception ignore) {
					}

					System.out.println("❌ API ERROR: " + reason);

					writeErrorLog(project, videoFile.getName(), e);

					// logUpload(videoFile.getName(), project, "FAILED", "", reason);
					appendReport("FAILED", project, sourceUrl, "", "", reason);

					if (reason.equalsIgnoreCase("quotaExceeded") || reason.equalsIgnoreCase("dailyLimitExceeded")
							|| reason.equalsIgnoreCase("rateLimitExceeded")) {

						markProjectQuotaExceeded(project);

						System.out.println("⚠ SWITCHING PROJECT...");

						continue;
					}

					if (reason.equalsIgnoreCase("accessNotConfigured")) {

						System.out.println("⚠ API NOT ENABLED");

						continue;
					}

					e.printStackTrace();

				} catch (Exception e) {

					writeErrorLog(project, videoFile.getName(), e);

					// logUpload(videoFile.getName(), project, "FAILED", "", e.getMessage());
					appendReport("FAILED", project, sourceUrl, "", "", e.getMessage());

					e.printStackTrace();
				}
			}

			System.out.println("\n❌ ALL PROJECTS FAILED");

			generateHtmlDashboard();

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	private static List<String> getAvailableProjects() throws Exception {

		return Files.list(Paths.get("credentials")).filter(Files::isDirectory).map(p -> p.getFileName().toString())
				.sorted().collect(Collectors.toList());
	}

	private static int getNextProjectIndex(int totalProjects) throws Exception {

		int index = 0;

		if (Files.exists(PROJECT_INDEX_FILE)) {

			String txt = Files.readString(PROJECT_INDEX_FILE).trim();

			if (!txt.isBlank()) {

				index = Integer.parseInt(txt);
			}
		}

		int next = (index + 1) % totalProjects;

		Files.writeString(PROJECT_INDEX_FILE, String.valueOf(next));

		return index;
	}

	private static boolean isProjectQuotaExceeded(String project) {

		try {

			if (!Files.exists(QUOTA_CACHE_FILE)) {
				return false;
			}

			List<String> lines = Files.readAllLines(QUOTA_CACHE_FILE);

			LocalDate today = LocalDate.now();

			for (String line : lines) {

				String[] parts = line.split("=");

				if (parts.length != 2) {
					continue;
				}

				String p = parts[0];

				LocalDate date = LocalDate.parse(parts[1]);

				if (p.equals(project) && date.equals(today)) {

					return true;
				}
			}

		} catch (Exception e) {

			e.printStackTrace();
		}

		return false;
	}

	private static void markProjectQuotaExceeded(String project) {

		try {

			String line = project + "=" + LocalDate.now();

			Files.write(QUOTA_CACHE_FILE, Arrays.asList(line), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	private static void logUpload(String video, String project, String status, String videoId, String error) {

		try {

			boolean exists = Files.exists(REPORT_CSV);

			try (BufferedWriter writer = Files.newBufferedWriter(REPORT_CSV, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND)) {

				if (!exists) {

					writer.write("time,video,project,status,videoId,error\n");
				}

				writer.write(LocalDateTime.now().format(REPORT_TIME) + "," + video + "," + project + "," + status + ","
						+ videoId + "," + error + "\n");
			}

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	private static void writeErrorLog(String project, String video, Exception e) {

		try {

			String fileName = System.currentTimeMillis() + "_" + project + ".log";

			Path logFile = LOG_DIR.resolve(fileName);

			StringWriter sw = new StringWriter();

			PrintWriter pw = new PrintWriter(sw);

			e.printStackTrace(pw);

			Files.writeString(logFile, sw.toString());

		} catch (Exception ex) {

			ex.printStackTrace();
		}
	}

	// ======================================================
	// REPORT APPEND METHOD
	// ======================================================

	private static void appendReport(String status, String project, String sourceUrl, String videoType, String videoUrl,
			String error) {
		videoType = "Video";

		try {

			Files.createDirectories(REPORT_DIR);

			boolean exists = Files.exists(REPORT_CSV);

			try (BufferedWriter writer = Files.newBufferedWriter(REPORT_CSV, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND)) {

				if (!exists) {

					writer.write("time,status,project,sourceUrl,videoType,videoUrl,error\n");
				}

				String time = LocalDateTime.now().format(REPORT_TIME);

				writer.write(csv(time) + "," + csv(status) + "," + csv(project) + "," + csv(sourceUrl) + ","
						+ csv(videoType) + "," + csv(videoUrl) + "," + csv(error) + "\n");
			}

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	// ======================================================
	// CSV SAFE VALUE
	// ======================================================

	private static String csv(String value) {

		if (value == null) {
			value = "";
		}

		value = value.replace("\"", "\"\"");

		return "\"" + value + "\"";
	}

	private static void generateHtmlDashboard() {

		try {

			if (!Files.exists(REPORT_CSV)) {
				return;
			}

			List<String> lines = Files.readAllLines(REPORT_CSV);

			StringBuilder html = new StringBuilder();

			html.append("""
					<html>
					<head>
					<title>YouTube Upload Dashboard</title>

					<style>

					body{
					    font-family:Arial;
					    background:#111;
					    color:white;
					    padding:20px;
					}

					table{
					    border-collapse:collapse;
					    width:100%;
					    background:#1a1a1a;
					}

					th,td{
					    border:1px solid #333;
					    padding:10px;
					    text-align:left;
					    font-size:14px;
					}

					th{
					    background:#222;
					    color:#00ff99;
					}

					tr:nth-child(even){
					    background:#181818;
					}

					.success{
					    color:#00ff99;
					    font-weight:bold;
					}

					.failed{
					    color:#ff5c5c;
					    font-weight:bold;
					}

					.skipped{
					    color:orange;
					    font-weight:bold;
					}

					a{
					    color:#4da6ff;
					    text-decoration:none;
					}

					a:hover{
					    text-decoration:underline;
					}

					</style>
					</head>
					<body>

					<h2>YouTube Upload Dashboard</h2>

					<table>
					""");

			boolean header = true;

			for (String line : lines) {

				List<String> cols = parseCsvLine(line);

				html.append("<tr>");

				for (int i = 0; i < cols.size(); i++) {

					String col = cols.get(i);

					if (header) {

						html.append("<th>").append(col).append("</th>");

					} else {

						// STATUS COLUMN
						if (i == 1) {

							String css = col.toLowerCase();

							html.append("<td class='").append(css).append("'>").append(col).append("</td>");
						}

						// VIDEO URL COLUMN
						else if (i == 5 && !col.isBlank()) {

							html.append("<td>").append("<a href='").append(col)
									.append("' target='_blank'>OPEN VIDEO</a>").append("</td>");
						}

						else {

							html.append("<td>").append(col).append("</td>");
						}
					}
				}

				html.append("</tr>");

				header = false;
			}

			html.append("""
					</table>
					</body>
					</html>
					""");

			Files.writeString(REPORT_HTML, html.toString());

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	private static String generateViralTitle(String baseTitle) {

		if (baseTitle == null)
			baseTitle = "";

		// REMOVE ALL PHOTO WORDS
		baseTitle = baseTitle.replaceAll("(?i)\\bIN PHOTOS\\b", "").replaceAll("(?i)\\bPHOTOS\\b", "")
				.replaceAll("(?i)\\bIN PICS\\b", "").replaceAll("(?i)\\bPICS\\b", "")
				.replaceAll("(?i)\\bPHOTO GALLERY\\b", "").replaceAll("\\s+", " ").trim();

		// CLEAN SYMBOLS
		baseTitle = baseTitle.replaceAll("[^a-zA-Z0-9 ]", "").trim();

		String[] hooks = { "You didnt notice this at first 👀", "This clip changed the internet 😱",
				"People cant stop talking about this 🔥", "This happened in seconds 😳",
				"One small detail changed everything 🤯", "This was totally unexpected 😮",
				"Everyone missed this hidden detail 👀", "This became instantly viral 🚀", "No one saw this coming 😱",
				"This left fans speechless 😳", "This video explains everything 😮",
				"Something strange happened here 🤔", "This moment broke the internet 🔥",
				"This deserves more attention 👀", "People are reacting to this everywhere 😲",
				"This is getting viral for all reasons 😳", "What happened here is unbelievable 😱",
				"This tiny moment means a lot 🤯", "This caught everyone off guard 😮",
				"Watch carefully before it disappears 👀", "This instantly became headline news 📰",
				"This surprised even the experts 😳", "Nobody can explain this properly 🤔",
				"This moment turned everything around 🔥", "One scene changed the whole story 😮",
				"People noticed this immediately 👀", "This clip has everyone confused 😵",
				"This detail says more than words 😳", "This reaction says it all 😲",
				"The internet is obsessed with this 🔥", "This happened when nobody expected it 😱",
				"This detail is impossible to ignore 👀", "Fans are divided over this 😮",
				"This moment feels unreal 😳", "This video keeps getting shared 🚀", "This was not part of the plan 😲",
				"Something unexpected happened here 😱", "People noticed this hidden clue 👀",
				"This raised eyebrows everywhere 🤨", "This story took a wild turn 😳",
				"This scene became instantly iconic 🔥", "You may have missed the best part 👀",
				"This clip shocked social media 😱", "This one detail changes everything 🤯",
				"People cant believe this is real 😮", "This went more viral than expected 🚀",
				"This has everyone talking right now 🔥", "This moment created huge buzz 😳",
				"This is why everyone is watching 👀", "This unexpected twist stunned everyone 😱" };

		String hook = hooks[new Random().nextInt(hooks.length)];

		String finalTitle = hook + " | " + baseTitle;

		// CLEAN AGAIN JUST IN CASE
		finalTitle = finalTitle.replaceAll("(?i)\\bIN PHOTOS\\b", "").replaceAll("(?i)\\bPHOTOS\\b", "")
				.replaceAll("(?i)\\bIN PICS\\b", "").replaceAll("(?i)\\bPICS\\b", "")
				.replaceAll("(?i)\\bPHOTO GALLERY\\b", "").replaceAll("\\s+", " ").trim();

		return finalTitle.length() > 100 ? finalTitle.substring(0, 100) : finalTitle;
	}

	private static List<String> parseCsvLine(String line) {

		List<String> result = new ArrayList<>();

		boolean inQuotes = false;

		StringBuilder sb = new StringBuilder();

		for (char c : line.toCharArray()) {

			if (c == '"') {

				inQuotes = !inQuotes;
			}

			else if (c == ',' && !inQuotes) {

				result.add(sb.toString());

				sb.setLength(0);
			}

			else {

				sb.append(c);
			}
		}

		result.add(sb.toString());

		return result;
	}

	private static String generateHashtags(List<String> tags) {
		return tags.stream().map(t -> "#" + t).limit(50) // keep clean
				.collect(Collectors.joining(" "));
	}

	private static String generateDescription(String title, String captions, String hashtags) {

		String desc = title + "\n\n" + captions + "\n\n" + hashtags;

		return desc.length() > 4900 ? desc.substring(0, 4900) : desc;
	}

	private static String normalizePossessive(String text) {
		if (text == null)
			return null;

		return text.replaceAll("’s", "s") // smart quote
				.replaceAll("'s", "s"); // normal apostrophe
	}

	// ================= HELPERS =================
	private static void downloadImageWithPlaywright(Page page, String url, Path out) throws IOException {
		APIResponse r = page.request().get(url);
		Files.write(out, r.body());
	}

	private static boolean addTextJava2D(Path in, Path out, String text) throws IOException {

		System.out.println("Reading Image: " + in);

		BufferedImage img = ImageIO.read(in.toFile());

		if (img == null) {
			System.out.println("❌ Unsupported/Invalid image: " + in);
			return false;
		}

		Graphics2D g = img.createGraphics();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		g.setFont(new Font("DejaVu Sans", Font.BOLD, FONT_SIZE));

		// NARROWER WIDTH = BETTER WRAPPING
		List<String> lines = wrapText(g, text, img.getWidth() - 120);

		FontMetrics fm = g.getFontMetrics();

		// int totalHeight = lines.size() * LINE_HEIGHT;

		// MOVE CAPTIONS HIGHER
		int y = img.getHeight() - 180;

		for (String line : lines) {

			int w = fm.stringWidth(line);

			int x = Math.max(40, (img.getWidth() - w) / 2);

			// BIGGER BACKGROUND
			g.setColor(new Color(0, 0, 0, 180));

			g.fillRoundRect(x - 25, y - fm.getAscent() - 10, w + 50, fm.getHeight() + 20, 25, 25);

			g.setColor(Color.WHITE);

			g.drawString(line, x, y);

			y += LINE_HEIGHT;
			// EXTRA SPACING BETWEEN LINES
			// y += LINE_HEIGHT + 10;
		}

		g.dispose();

		ImageIO.write(img, "jpg", out.toFile());

		img.flush();

		return true;
	}

	private static List<String> wrapText(Graphics2D g, String text, int maxWidth) {

		List<String> lines = new ArrayList<>();

		if (text == null || text.isBlank()) {
			return lines;
		}

		FontMetrics fm = g.getFontMetrics();

		String[] words = text.split("\\s+");

		StringBuilder line = new StringBuilder();

		for (String word : words) {

			String testLine;

			if (line.length() == 0) {
				testLine = word;
			} else {
				testLine = line + " " + word;
			}

			int width = fm.stringWidth(testLine);

			if (width <= maxWidth) {

				line = new StringBuilder(testLine);

			} else {

				if (line.length() > 0) {
					lines.add(line.toString());
				}

				line = new StringBuilder(word);
			}
		}

		if (line.length() > 0) {
			lines.add(line.toString());
		}

		return lines;
	}

	private static void runFFmpeg(List<String> cmd, String step) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);

		Process p = pb.start();

		BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

		String line;

		while ((line = reader.readLine()) != null) {
			System.out.println(line);
		}
		if (p.waitFor() != 0)
			throw new RuntimeException("FFmpeg failed: " + step);
	}

	private static void createTextFile(Path dir, String title, List<String> caps) throws IOException {
		Files.write(dir.resolve(sanitizeForFolderName(title) + ".txt"), caps);
	}

	private static Path getRandomMusicFile() throws IOException {

		// ✅ Create folder automatically if missing
		if (!Files.exists(MUSIC_DIR)) {
			Files.createDirectories(MUSIC_DIR);

			System.out.println("⚠ Music folder created:");
			System.out.println(MUSIC_DIR.toAbsolutePath());

			return null;
		}

		List<Path> files = Files.list(MUSIC_DIR).filter(p -> p.toString().matches(".*\\.(mp3|wav|aac)$"))
				.collect(Collectors.toList());

		// ✅ No music found
		if (files.isEmpty()) {
			System.out.println("⚠ No music files found in: " + MUSIC_DIR);
			return null;
		}

		return files.get(RANDOM.nextInt(files.size()));
	}

//	private static boolean generateTTS(String text, Path outputPath) {
//		try {
//			if (text == null || text.isBlank()) {
//				text = "Hello";
//			}
//
//			text = text.replace("\"", "").replace("'", "");
//
//			String voice = "en-IN-NeerjaNeural";
//
//			ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "edge-tts --voice " + voice + " --text \"" + text + "\""
//					+ " --write-media \"" + outputPath.toAbsolutePath() + "\"");
//
//			pb.inheritIO();
//			Process process = pb.start();
//
//			int exitCode = process.waitFor();
//
//			// ✅ VERY IMPORTANT CHECK
//			if (exitCode == 0 && Files.exists(outputPath) && Files.size(outputPath) > 1000) {
//				return true;
//			}
//
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//
//		return false;
//	}

	private static boolean generateTTS(String text, Path outputPath) {

		try {

			if (text == null || text.isBlank()) {
				text = "Hello";
			}

			// CLEAN TEXT
			text = text.replace("\"", "").replace("'", "").replace("\n", " ").replace("\r", " ").trim();

			// LIMIT VERY LONG TEXT
			if (text.length() > 300) {
				text = text.substring(0, 300);
			}

			String voice = "en-IN-NeerjaNeural";
//			String voice;
//
//			if (text.length() > 120) {
//				voice = "en-US-JennyNeural";
//			} else {
//				voice = "en-IN-NeerjaNeural";
//			}

			List<String> command = new ArrayList<>();

			// command.add("edge-tts");
			command.add("python");
			command.add("-m");
			command.add("edge_tts");

			command.add("--voice");
			command.add(voice);

			command.add("--text");
			command.add(text);

			command.add("--write-media");
			command.add(outputPath.toAbsolutePath().toString());
			System.out.println("Running TTS: " + command);

			ProcessBuilder pb = new ProcessBuilder(command);

			pb.redirectErrorStream(true);

			Process process = pb.start();

			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line;

			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}

			int exitCode = process.waitFor();

			if (exitCode == 0 && Files.exists(outputPath) && Files.size(outputPath) > 1000) {

				System.out.println("✅ TTS CREATED: " + outputPath);

				return true;
			}

			System.out.println("❌ TTS FAILED");

		} catch (Exception e) {

			e.printStackTrace();
		}

		return false;
	}

	private static void createSilentAudio(Path out) throws Exception {
		runFFmpeg(List.of("ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc", "-t", "2", "-q:a", "9", "-acodec",
				"libmp3lame", out.toString()), "Silent Audio");
	}

//	private static void renderCinematicTimeline(List<SceneData> scenes, Path output) throws Exception {
//
//		if (scenes.isEmpty()) {
//			throw new RuntimeException("No scenes generated");
//		}
//
//		if (scenes.size() == 1) {
//
//			Files.copy(scenes.get(0).segment, output, StandardCopyOption.REPLACE_EXISTING);
//
//			return;
//		}
//
//		List<String> cmd = new ArrayList<>();
//
//		cmd.add("ffmpeg");
//		cmd.add("-y");
//
//		// ================= INPUTS =================
//
//		for (SceneData s : scenes) {
//
//			cmd.add("-i");
//			cmd.add(s.segment.toString());
//		}
//
//		StringBuilder filter = new StringBuilder();
//
//		// ================= VIDEO CHAIN =================
//
//		double offset = scenes.get(0).duration - 1.0;
//
//		filter.append("[0:v][1:v]").append("xfade=transition=").append(scenes.get(1).transition).append(":duration=1")
//				.append(":offset=").append(offset).append("[v1];");
//
//		for (int i = 2; i < scenes.size(); i++) {
//
//			offset += scenes.get(i - 1).duration - 1.0;
//
//			filter.append("[v").append(i - 1).append("][").append(i).append(":v]")
//
//					.append("xfade=transition=").append(scenes.get(i).transition)
//
//					.append(":duration=1").append(":offset=").append(offset)
//
//					.append("[v").append(i).append("];");
//		}
//
//		// ================= AUDIO CHAIN =================
//
//		filter.append("[0:a][1:a]").append("acrossfade=d=1[a1];");
//
//		for (int i = 2; i < scenes.size(); i++) {
//
//			filter.append("[a").append(i - 1).append("][").append(i).append(":a]")
//
//					.append("acrossfade=d=1")
//
//					.append("[a").append(i).append("];");
//		}
//
//		cmd.add("-filter_complex");
//		cmd.add(filter.toString());
//
//		// ================= MAP FINAL =================
//
//		cmd.add("-map");
//		cmd.add("[v" + (scenes.size() - 1) + "]");
//
//		cmd.add("-map");
//		cmd.add("[a" + (scenes.size() - 1) + "]");
//
//		cmd.add("-c:v");
//		cmd.add("libx264");
//
//		cmd.add("-preset");
//		cmd.add("medium");
//
//		cmd.add("-crf");
//		cmd.add("20");
//
//		cmd.add("-pix_fmt");
//		cmd.add("yuv420p");
//
//		cmd.add("-c:a");
//		cmd.add("aac");
//
//		cmd.add(output.toString());
//
//		runFFmpeg(cmd, "Cinematic Timeline");
//	}

	private static String sanitizeForFolderName(String s) {

		if (s == null || s.isBlank()) {
			return "output_" + System.currentTimeMillis();
		}

//		String cleaned = s.replaceAll("[\\\\/:*?\"<>|!]", "") // illegal chars
//				.replaceAll("[']", "") // remove apostrophe
//				.replaceAll("[^a-zA-Z0-9 ]", "") // safe chars only
//				.replaceAll("\\s+", " ") // normalize spaces
//				.replaceAll("Ent Top Stories", "").replaceAll("(?i)in photos", "").replaceAll("Ent Top Stories", "")
//				.replaceAll("(?i)photos", "").replaceAll("(?i)pics/", "").replaceAll("(?i)in pics", "").trim();

		String cleaned = s.replaceAll("[\\\\/:*?\"<>|!]", "").replaceAll("[']", "").replaceAll("\\s+", " ")
				.replaceAll("(?i)ent top stories", "").replaceAll("(?i)in photos", "").replaceAll("(?i)photos", "")
				.replaceAll("(?i)pics", "").replaceAll("(?i)photo gallery", "").replaceAll("(?i)viral pics", "")
				.replaceAll("(?i)exclusive photos", "").replaceAll("(?i)see pics", "").trim();

		// ✅ limit length safely
		if (cleaned.length() > 100) {
			cleaned = cleaned.substring(0, 100);
		}

		// ✅ VERY IMPORTANT
		cleaned = cleaned.trim();

		// ✅ Windows cannot end with dot
		cleaned = cleaned.replaceAll("[. ]+$", "");

		if (cleaned.isBlank()) {
			return "video_" + System.currentTimeMillis();
		}

		return cleaned;
	}
}
