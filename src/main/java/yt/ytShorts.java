package yt;

import com.google.api.client.auth.oauth2.Credential;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ytShorts {

	// ================= CONFIG =================
	private static final String IMAGE_PREFIX = "img";

	private static final int TARGET_WIDTH = 1080;
	private static final int TARGET_HEIGHT = 1920;

//	private static final int MAX_CHARS_PER_LINE = 40;
//	private static final int FONT_SIZE = 48;
//	private static final int LINE_HEIGHT = 56;
//	private static final int START_Y = 80;

	private static final int MAX_CHARS_PER_LINE = 55;
	private static final int FONT_SIZE = 40; // 30
	private static final int LINE_HEIGHT = 50; // 56
	private static final int START_Y = 70; // 50

	private static final double SECONDS_PER_IMAGE = 3.0;
	private static final int FPS = 30;

	private static final String APPLICATION_NAME = "YouTubeUploaderClient";
	private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter.ofPattern("HH:mm | dd MMM yyyy");

	private static final Path REPORT_DIR = Paths.get("reports");

	private static final Path REPORT_CSV = REPORT_DIR.resolve("upload_report.csv");

	private static final Path REPORT_HTML = REPORT_DIR.resolve("dashboard.html");

	private static final Path LOG_DIR = REPORT_DIR.resolve("logs");

	private static final Path STATE_DIR = Paths.get("state");

	private static final Path PROJECT_INDEX_FILE = STATE_DIR.resolve("project_index.txt");

	private static final Path QUOTA_CACHE_FILE = STATE_DIR.resolve("quota_exceeded.txt");

	private static final Path MUSIC_DIR = Paths.get("music");
	private static final Random RANDOM = new Random();

	public static String safeTitle;
	public static String pageTitle;
	public static String allCaptions;
	public static String finalOutput;
	public static Page page;

	public static String descriptionDefault = "This video captures a trending celebrity fashion moment that quickly gained attention across social media. From red carpet appearances to high-profile events and TV studios, these looks showcase confidence, style, and modern celebrity culture.\r\n"
			+ "\r\n"
			+ "Watch this YouTube Short to revisit a standout appearance that fans are sharing everywhere. Celebrity fashion moments like these often become viral for their bold styling, event presence, and pop culture impact.\r\n"
			+ "\r\n"
			+ "Stay tuned for more trending Shorts featuring celebrity appearances, red carpet looks, and viral fashion moments updated regularly.\r\n"
			+ "\r\n" + "#Shorts, #YouTubeShorts, #ViralMoments, #TrendingShorts, #CelebrityFashion,\r\n"
			+ "#RedCarpetStyle, #CelebrityLook, #FashionMoment, #PopCulture,\r\n"
			+ "#EntertainmentNews, #ModelStyle, #CelebrityStyle, #ViralVideo,\r\n"
			+ "#HollywoodBuzz, #StyleInspo, #bollywood, #bollywoodfashion, #dress, #celebsdress, #celebslook, #cricket, #ipl2026, #news, #politics";
	// ==========================================

	public static void main(String[] args) throws Exception {

		List<String> urls = Files.readAllLines(Paths.get("urls.txt"), StandardCharsets.UTF_8).stream().map(String::trim)
				.filter(s -> !s.isBlank()).collect(Collectors.toList());

		for (String url : urls) {
			System.out.println("=================================");
			System.out.println(" PROCESSING URL ");
			System.out.println(url);
			System.out.println("=================================");

			List<String> captions = new ArrayList<>();

			try (Playwright pw = Playwright.create()) {

				Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

				BrowserContext context = browser.newContext();
				page = context.newPage();

				page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				page.waitForTimeout(5000);

				// String pageTitle = page.locator("h1 span").last().textContent().trim();
//				String pageTitle = page.locator("(//h1)[1]").innerText().trim();
//				safeTitle = sanitizeForFolderName(pageTitle);
//				System.out.println("PAGE (SAFETITLE) : " + safeTitle);
				getPageTitle();

				Path shortsRoot = Paths.get("shorts");
				Files.createDirectories(shortsRoot);
				Path baseDir = shortsRoot.resolve(safeTitle);

				Path downloadDir = baseDir.resolve("downloaded_images");
				Path resizedDir = baseDir.resolve("resized_images");
				Path finalDir = baseDir.resolve("final_images_with_text");

				Files.createDirectories(downloadDir);
				Files.createDirectories(resizedDir);
				Files.createDirectories(finalDir);

//				List<Locator> images = page.locator("//div[" + "contains(@class,'entertainment-background') or "
//						+ "contains(@class,'news-background') or " + "contains(@class,'sports-background') or "
//						+ "contains(@class,'mumbai-background') or " + "contains(@class,'mumbai-guide-background')"
//						+ "]//img[@alt]").all();

				List<Locator> cards = page.locator("div.photos_cardposition__71WWM").all();
				System.out.println("TOTAL CARDS: " + cards.size());

				int downloadIndex = 1;

				for (Locator card : cards) {

					try {

						Locator img = card.locator("div.main-img img");

						String src = img.getAttribute("src");

						// Skip ads
						if (card.innerText().contains("ADVERTISEMENT"))
							continue;

						// Lazy load fallback
						if (src == null || src.isEmpty()) {
							src = img.getAttribute("data-src");
						}

						if (src == null || src.isEmpty()) {

							String srcset = img.getAttribute("srcset");

							if (srcset != null && !srcset.isEmpty()) {
								src = srcset.split(",")[0].split(" ")[0];
							}
						}

						if (src == null || src.isEmpty())
							continue;

						String caption;

						// FIRST IMAGE = FULL TITLE
						if (downloadIndex == 1) {

							Locator spanNode = card.locator("div.photos_photogallytitlepos__PgJ0k span");

							caption = spanNode.innerText().trim();

						} else {

							Locator firstPara = card.locator("div.photos_photogallytitlepos__PgJ0k span p").first();

							caption = firstPara.innerText().trim();
						}

						// FIRST SENTENCE ONLY
						caption = getFirstSentence(caption);

						// REMOVE PHOTO CREDITS
						caption = caption.replaceAll("\\(Pics?/.*?\\)", "");
						caption = caption.replaceAll("\\(Images?/.*?\\)", "");
						caption = caption.replaceAll("\\(Photo[s]?/.*?\\)", "");
						caption = caption.replaceAll("\\(Pic credit:.*?\\)", "");

						caption = normalizePossessive(caption);

						caption = cleanText(caption);

						caption = caption.replaceAll("\\s+", " ").trim();

						System.out.println("[" + downloadIndex + "] CAPTION: " + caption);

						Path out = downloadDir.resolve(String.format("%s%03d.jpg", IMAGE_PREFIX, downloadIndex));

						try {

							downloadImageWithPlaywright(page, src, out);

							if (Files.exists(out) && Files.size(out) > 1000) {

								captions.add(caption);

								downloadIndex++;

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

				createTextFile(baseDir, pageTitle, captions);
				browser.close();

				List<Path> downloadedImages = Files.list(downloadDir).sorted().collect(Collectors.toList());
				int resizeIndex = 1;

				for (Path img : downloadedImages) {
					Path out = resizedDir.resolve(String.format("%s%03d.jpg", IMAGE_PREFIX, resizeIndex++));

					runFFmpeg(List.of("ffmpeg", "-y",

							"-i", img.toAbsolutePath().toString(),

							"-vf",
							"scale=1080:1920:force_original_aspect_ratio=decrease,"
									+ "pad=1080:1920:(ow-iw)/2:(oh-ih)/2",

							// ✅ IMPORTANT FIX
							"-frames:v", "1", "-update", "1",

							out.toAbsolutePath().toString()

					), "Resize");
				}

				List<Path> resizedImages = Files.list(resizedDir).sorted().collect(Collectors.toList());

				int videoIndex = 1;

				// ✅ IMPORTANT FIX
				int safeSize = Math.min(resizedImages.size(), captions.size());

				for (int i = 0; i < safeSize; i++) {

					Path input = resizedImages.get(i);

					Path output = finalDir.resolve(String.format("%s%03d.jpg", IMAGE_PREFIX, videoIndex));

					if (addTextJava2D(input, output, captions.get(i))) {
						videoIndex++;
					}
				}

				if (videoIndex == 1) {

					System.out.println("❌ No final images generated. Skipping video.");

					continue;
				}
				Path audio = getRandomMusicFile();
				System.out.println("🎵 Selected audio: " + audio.getFileName());

				Path finalVideo = baseDir.resolve(safeTitle + ".mp4");

				runFFmpeg(
						List.of("ffmpeg", "-y", "-framerate", "1/" + SECONDS_PER_IMAGE, "-i",
								finalDir.resolve("img%03d.jpg").toString(), "-stream_loop", "-1", "-i",
								audio.toAbsolutePath().toString(), "-c:v", "libx264", "-c:a", "aac", "-shortest", "-r",
								String.valueOf(FPS), "-pix_fmt", "yuv420p", finalVideo.toString()),
						"Final Video + Audio");

				System.out.println("UPLOAD VIDEO TO YT: ");
				Upload(finalVideo.toFile()); // ✅ UPDATED
			}
		}
	}

	private static String getFirstSentence(String text) {

		if (text == null || text.isBlank())
			return text;

		String[] parts = text.split("(?<=[.!?])\\s+");

		return parts.length > 0 ? parts[0].trim() : text.trim();
	}

	private static String normalizePossessive(String text) {

		if (text == null)
			return null;

		return text.replaceAll("’s", "s").replaceAll("'s", "s");
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

	private static String extractPlainText(String rawTag) {

		// ✅ NEW: cut everything after title=
		rawTag = rawTag.split("title=")[0];

		String cleaned = rawTag.replaceAll("<img", "").replaceAll(">", "").trim();

		String[] parts = cleaned.split("=\"\"");

		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			part = part.trim();

			if (part.equals("alt") || part.isEmpty())
				continue;

			result.append(part).append(" ");
		}

//		String text = result.toString().replaceAll("\\s+", " ").trim();
//
//		// keep inner quotes only
//		text = text.replaceAll("^\"+", "");
//		text = text.replaceAll("\"+$", "");
//		text = text.replace("&ndash;", " ").replace("–", " ").replaceAll("\\s+", " ").trim();
//
//		return text;

		String text = result.toString().replaceAll("\\s+", " ").trim();

		// keep inner quotes only
		text = text.replaceAll("^\"+", "");
		text = text.replaceAll("\"+$", "");

		// CLEAN HTML ENTITIES + REMOVE JUNK
		text = text

				.replace("&rdquo;", "\"").replace("&ldquo;", "\"").replace("&rsquo;", "'").replace("&lsquo;", "'")
				.replace("&amp;", "&").replace("&nbsp;", " ").replace("&mdash;", "-").replace("&ndash;", " ")
				.replace("–", " ").replace("&hellip;", "...").replace("&quot;", "\"")

				// REMOVE THIS TEXT
				.replaceAll("(?i)Read full story here[,.]?", " ")

				// REMOVE EXTRA SPACES
				.replaceAll("\\s+", " ").trim();

		return text;
	}

	private static String cleanText(String text) {

		if (text == null)
			return "";

		text = text

				// HTML entities
				.replace("&ldquo;", "\"").replace("&rdquo;", "\"").replace("&lsquo;", "'").replace("&rsquo;", "'")
				.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&hellip;", "...")
				.replace("&mdash;", "-").replace("&ndash;", " ").replace("–", " ")

				// REMOVE THIS EVERYWHERE
				.replaceAll("(?i)Read full story here[,.]?", " ")

				// REMOVE MULTIPLE COMMAS
				.replaceAll(",\\s*,", ",")

				// REMOVE EXTRA SPACES
				.replaceAll("\\s+", " ").trim();

		return text;
	}

	private static void Upload(File videoFile) {

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

					Video videoObject = new Video();

					VideoSnippet snippet = new VideoSnippet();

					String fullTitle = cleanText(safeTitle) + " #shorts #shortvideo";

					String finalTitle = fullTitle.length() > 100 ? fullTitle.substring(0, 100) : fullTitle;

					snippet.setTitle(finalTitle);

					String finalDesc = cleanText(safeTitle) + "\n\n" + cleanText(allCaptions) + "\n";

					finalDesc = finalDesc.length() > 5000 ? finalDesc.substring(0, 5000) : finalDesc;

					snippet.setDescription(finalDesc);

					split_word_add_Hashtag();

					snippet.setTags(Collections.singletonList(finalOutput + "#Shorts,#YouTubeShorts,"
							+ "#ViralMoments,#TrendingShorts," + "#CelebrityFashion,#RedCarpetStyle,"
							+ "#CelebrityLook,#FashionMoment," + "#PopCulture,#EntertainmentNews,"
							+ "#ModelStyle,#CelebrityStyle," + "#ViralVideo,#HollywoodBuzz," + "#StyleInspo,#bollywood,"
							+ "#bollywoodfashion,#dress," + "#celebsdress,#celebslook," + "#shortvideo,#cricket,"
							+ "#ipl2026,#news,#politics"));

					videoObject.setSnippet(snippet);

					VideoStatus status = new VideoStatus();

					status.setPrivacyStatus("public");

					videoObject.setStatus(status);

					InputStreamContent mediaContent = new InputStreamContent("video/*", new FileInputStream(videoFile));

					YouTube.Videos.Insert videoInsert = youtube.videos().insert("snippet,status", videoObject,
							mediaContent);

					Video returnedVideo = videoInsert.execute();

					String videoUrl = "https://youtube.com/watch?v=" + returnedVideo.getId();

					System.out.println("✅ UPLOADED SUCCESSFULLY");

					System.out.println(videoUrl);

					appendReport("SUCCESS", project, "", returnedVideo.getId(), videoUrl, "");

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

					appendReport("FAILED", project, "", "", "", reason);

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

					appendReport("FAILED", project, "", "", "", e.getMessage());

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

	private static void appendReport(String status, String project, String sourceUrl, String videoId, String videoUrl,
			String error) {

		try {

			Files.createDirectories(REPORT_DIR);

			boolean exists = Files.exists(REPORT_CSV);

			try (BufferedWriter writer = Files.newBufferedWriter(REPORT_CSV, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND)) {

				if (!exists) {

					writer.write("TIME,STATUS,project,sourceUrl,videoId,videoUrl,error\n");
				}

				String time = LocalDateTime.now().format(REPORT_TIME);

				writer.write(csv(time) + "," + csv(status) + "," + csv(project) + "," + csv(sourceUrl) + ","
						+ csv(videoId) + "," + csv(videoUrl) + "," + csv(error) + "\n");
			}

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	private static String csv(String value) {

		if (value == null) {
			value = "";
		}

		value = value.replace("\"", "\"\"");

		return "\"" + value + "\"";
	}

	private static List<String> parseCsvLine(String line) {

		List<String> result = new ArrayList<>();

		boolean inQuotes = false;

		StringBuilder sb = new StringBuilder();

		for (char c : line.toCharArray()) {

			if (c == '"') {

				inQuotes = !inQuotes;

			} else if (c == ',' && !inQuotes) {

				result.add(sb.toString());

				sb.setLength(0);

			} else {

				sb.append(c);
			}
		}

		result.add(sb.toString());

		return result;
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
					<title>YouTube Shorts Upload Dashboard</title>

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

					<h2>YouTube Shorts Upload Dashboard</h2>

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

						if (i == 1) {

							String css = col.toLowerCase();

							html.append("<td class='").append(css).append("'>").append(col).append("</td>");
						}

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

	public static void split_word_add_Hashtag() {
		String input = cleanText(safeTitle);
		// Split by space
		String[] words = input.split(" ");

		System.out.print("split_word_add_Hashtag :: \n\t");
		// String input = "my name is hidden in the world";
		// String[] words = input.split(" ");

		StringBuilder result = new StringBuilder();

		for (String word : words) {
			result.append("#").append(word).append(", ");
		}

		// remove last comma + space
		if (result.length() > 0) {
			result.setLength(result.length() - 2);
		}

		finalOutput = result.toString();
		System.out.println(finalOutput);

		// Process each word
		// for (String word : words) {
		// transformed = "#" + word + ",";
		// System.out.print(transformed);
		// }
	}

	public static Credential authorize(String selectedProject) throws Exception {

		System.out.println("USING PROJECT: " + selectedProject);

		String credentialPath = "credentials/" + selectedProject + "/client_secret.json";

		String tokenPath = "tokens/" + selectedProject;

		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(),
				new InputStreamReader(new FileInputStream(credentialPath)));

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), clientSecrets,
				Collections.singletonList("https://www.googleapis.com/auth/youtube.upload")).setAccessType("offline")
				.setDataStoreFactory(new FileDataStoreFactory(new File(tokenPath))).build();

		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

		return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
	}

	// ================= AUDIO =================
	private static Path getRandomMusicFile() throws IOException {
		List<Path> files = Files.list(MUSIC_DIR).filter(p -> p.toString().matches(".*\\.(mp3|wav|aac)$"))
				.collect(Collectors.toList());

		if (files.isEmpty())
			throw new RuntimeException("No audio files found!");

		return files.get(RANDOM.nextInt(files.size()));
	}

	// ================= HELPERS =================
	private static void downloadImageWithPlaywright(Page page, String url, Path out) throws IOException {
		APIResponse r = page.request().get(url);
		Files.write(out, r.body());
	}

	private static boolean addTextJava2D(Path in, Path out, String text) throws IOException {

		text = cleanText(text);

		BufferedImage img = ImageIO.read(in.toFile());
		Graphics2D g = img.createGraphics();
		g.setFont(new Font("DejaVu Sans", Font.BOLD, FONT_SIZE));

		List<String> lines = wrapText(g, text, 900);
		FontMetrics fm = g.getFontMetrics();
		int y = START_Y;

		for (String line : lines) {
			int w = fm.stringWidth(line);
			int x = Math.max(40, (img.getWidth() - w) / 2);

			g.setColor(new Color(0, 0, 0, 180));
			g.fillRoundRect(x - 20, y - fm.getAscent(), w + 40, fm.getHeight(), 20, 20);

			g.setColor(Color.WHITE);
			g.drawString(line, x, y);
			y += LINE_HEIGHT;
		}

		g.dispose();
		ImageIO.write(img, "jpg", out.toFile());
		return true;
	}

	private static List<String> wrapText(Graphics2D g, String text, int maxWidth) {

		List<String> lines = new ArrayList<>();

		FontMetrics fm = g.getFontMetrics();

		String[] words = text.split("\\s+");

		StringBuilder line = new StringBuilder();

		for (String word : words) {

			String testLine = line.length() == 0 ? word : line + " " + word;

			int width = fm.stringWidth(testLine);

			if (width < maxWidth) {

				line = new StringBuilder(testLine);

			} else {

				lines.add(line.toString());

				line = new StringBuilder(word);
			}
		}

		if (line.length() > 0) {
			lines.add(line.toString());
		}

		return lines;
	}

	private static void runFFmpeg(List<String> cmd, String step) throws IOException, InterruptedException {

		System.out.println("▶ FFmpeg " + step);
		Process p = new ProcessBuilder(cmd).inheritIO().start();
		if (p.waitFor() != 0)
			throw new RuntimeException("FFmpeg failed: " + step);
	}

	private static void createTextFile(Path dir, String title, List<String> caps) throws IOException {
		Path f = dir.resolve(sanitizeForFolderName(title) + ".txt");
		Files.write(f, caps);
		allCaptions = cleanText(caps.toString());
		System.out.println("\n\n>>>>>>>\n\t" + allCaptions);
	}

	private static String sanitizeForFolderName(String s) {
		s = s.replaceAll("(?i)in photos", "").trim();
		s = s.replaceAll("(?i)photos", "").trim();
		s = s.replaceAll("(?i)in pics", "").trim();
		s = s.replaceAll("Ent Top Stories", "").trim();
		return s.replaceAll("[\\\\/:*?\"<>|!]", "").trim();
	}
}
