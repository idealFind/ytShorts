package yt;

import com.microsoft.playwright.*;
import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MidDayTest {

	public static void main(String[] args) {

		// Initialize Playwright
		try (Playwright playwright = Playwright.create()) {
			// Launch the browser (headless mode can be enabled by passing true)
			Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
			BrowserContext context = browser.newContext();
			Page page = context.newPage();

			// Open the URL
			// String url = "https://www.mid-day.com/entertainment/entertainment-photos";
			// String url = "https://www.mid-day.com/mumbai/mumbai-photos";
			// String url = "https://www.mid-day.com/lifestyle/lifestyle-photos";
			// String url = "https://www.mid-day.com/news/news-photos";
			// String url = "https://www.mid-day.com/sports/sports-photos";

			// ALL CATEGORY URLS
			List<String> categoryUrls = Arrays.asList("https://www.mid-day.com/entertainment/entertainment-photos",
					"https://www.mid-day.com/mumbai/mumbai-photos",
					"https://www.mid-day.com/lifestyle/lifestyle-photos", "https://www.mid-day.com/news/news-photos",
					"https://www.mid-day.com/sports/sports-photos");

			// Store all final URLs
			List<String> urls = new ArrayList<>();

			for (String url : categoryUrls) {

				System.out.println("\nOPENING CATEGORY: " + url);

				page.navigate(url);
				page.waitForTimeout(4000);

				Locator blocks = page.locator("div.border-bottom.pb-3.mb-3, div.row.desktop-border.mb-4");

				for (int i = 0; i < blocks.count(); i++) {

					Locator block = blocks.nth(i);

					Locator timeLocator = block.locator("p.subcategories_toppicksdatetime__urVKO span");

					if (timeLocator.count() == 0)
						continue;

					String updated = timeLocator.first().innerText().trim();

					// ONLY mins/hours ago
					if (updated.matches("Updated \\d+ (mins|hours|hour) ago")) {

						Locator linkLocator = block.locator("a[href]").first();

						String href = linkLocator.getAttribute("href");

						if (href != null && href.startsWith("/")) {
							href = "https://www.mid-day.com" + href;
						}

						if (href != null && !href.isBlank()) {

							// AVOID DUPLICATES
							if (!urls.contains(href)) {

								urls.add(href);

								System.out.println("Time: " + updated);
								System.out.println("Link: " + href);
								System.out.println("====================");
							}
						}
					}
				}
			}

			// ================= WRITE TO FILE =================

			// File path
			Path filePath = Paths.get("urls.txt");

			// Clear old contents + write new URLs
			Files.write(filePath, urls, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);

			System.out.println("Saved " + urls.size() + " URLs to urls.txt");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
