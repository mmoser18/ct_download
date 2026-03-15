/**
 * Copyright © 2024-2025 by Michael Moser
 * Released under GPL V3 or later
 *
 * @author mmo / Michael Moser / 17732576+mmoser18@users.noreply.github.com
 */

package mmo.utils.heise_download;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.locators.RelativeLocator;

@Slf4j
@ToString
public class Download_Heise
{
	enum Magazine {
		ct   ("ct",   "c't",  "ct"),
		make ("make", "Make", "ch"); // re. "ch": oddly, the Make-download-files are named "ch.year.issue"

		final String urlFragment;
		final String labelFragment;
		final String filenamePrefix;

		Magazine(final String urlFragment, final String labelFragment, final String filenamePrefix) {
			this.urlFragment = urlFragment;
			this.labelFragment = labelFragment;
			this.filenamePrefix = filenamePrefix;
		}
	}

	private final static int DownloadMaxWait = 200; // [seconds] max. completion wait time before a download is considered failed
	private final static int AppearanceDefaultWait = 10; // [seconds]
	private final static String DefaultDownloadPath = (System.getProperty("os.name").startsWith("Windows")
	                                                  ? System.getProperty("user.home", "U:") // assuming "U:" points to user's home directory
	                                                  : "~") // for *ix and Mac
	                                                  + File.separator + "Downloads";

	// populated via processCommandLine():
	private Magazine magazine;
	private String downloadPath = DefaultDownloadPath;
	private String targetPath;
	private String usr;
	private String pwd;

	// populated via init():
	private String baseUrl;
	private String buttonLabel;
	private Pattern buttonLabelPattern;
	private String issueFileName;

	private WebDriver driver;

	protected void init() {
		this.baseUrl = String.format("https://www.heise.de/select/%s/archiv", magazine.urlFragment);
		this.buttonLabel = String.format("%s (((\\d{1,2})/)|(Jahresrückblick ))(\\d{4})", magazine.labelFragment);
		this.buttonLabelPattern = Pattern.compile(buttonLabel);
		this.issueFileName = "%1$s.%3$s.%4$s.pdf"; // %1: filename_prefix, %2: jahrgang, %3: last two digits of jahrgang, %4: issue-nr.
	}

	@SuppressWarnings("removal")
	@Override
	protected void finalize() throws Throwable {
		closeBrowser();
		super.finalize();
	}

	void setUpBrowser() {
		log.info("setUpBrowser.");
		// Initialize ChromeDriver.
		driver = new ChromeDriver();

		// Maximize the browser window size.
		// driver.manage().window().maximize();

		// Navigate to the website.
		log.info("navigating to '{}':", baseUrl);
		driver.get(baseUrl);
	}

	void getRidOfCookieGarbage() throws Exception {
		WebDriver frameDriver = null;
	  outer:
		for (int n = 0; n < 10; n++) {
			Thread.sleep(1000);

			// the login-panel is an iframe - so we first need to find the correct one,
			// i.e. the one whose name starts with "piano-id-":
			//finding all the web elements using iframe tag
			List<WebElement> iframeElements = driver.findElements(By.tagName("iframe"));
			log.debug("Total number of iframes found: {}", iframeElements.size());

			for (int i = 0; i < iframeElements.size(); i++) {
				WebElement iframe = iframeElements.get(i);
				String name = iframe.getDomAttribute("title");
				log.debug("Frame-name: '" + name + "'");
				if (name != null && name.equals("Cookie- und Datenverarbeitung")) {
					log.info("iframe for cookie garbage found: '{}': {}", name, iframe);
					frameDriver = driver.switchTo().frame(iframe);
					log.info("switched to frame {}: '{}'", i, name);
					break outer;
				}
			}
		}
		if (frameDriver != null) {
			WebElement ablehnenButton = waitForAppearance("sp_choice_type_13", 5);
			if (ablehnenButton != null) {
				log.info("Clicking '{}'", ablehnenButton.getText());
				ablehnenButton.click();
				Thread.sleep(3000);
			}
			driver.switchTo().defaultContent();
		} else {
			log.info("No cookie and trackers nuissance detected.");
		}
	}

	List<IssueDescriptor> loadListOfLastIssues() throws Exception {
		// Ensure that the user has reached https://www.heise.de/select/ct/archiv:
		final WebElement textOnHomePage = driver.findElement(By.xpath("//h1[contains(text(),\"Artikel-Archiv " + magazine.labelFragment + "\")]"));
		if (!textOnHomePage.isDisplayed()) {
			throw new Exception("The user hasn't arrived at Artikel-Archiv " + magazine.labelFragment + ".");
		}



		final WebElement anmeldenButton = driver.findElement(By.xpath("//span[contains(.,'Anmelden')]"));
		log.debug("loginButton=" + anmeldenButton);
		if (anmeldenButton.isDisplayed()) { // we are not logged-in, yet.
			log.info("\"Anmelden\" is displayed - logging in:");
			anmeldenButton.click();
			// filling out the login form:
			final WebElement loginUser = waitForAppearance(By.id("login-user"), 3); // give the site a few seconds to display the login-form...
			final WebElement loginPassword = driver.findElement(By.id("login-password"));
			final WebElement loginSubmit = driver.findElement(By.name("rm_login"));
			log.trace("entering user-id: '{}'", usr);
			loginUser.sendKeys(usr);
			log.trace("entering password: '{}'", pwd);
			loginPassword .sendKeys(pwd);
			log.info("clicking '{}':", loginSubmit.getText());
			loginSubmit.click();
			log.info("we should be logged-in now...");
		} else {
			log.info("'Anmelden' is NOT displayed - assuming that we already logged in.");
		}

		final WebElement archiveHeader = waitForAppearance("archive__header");
		final List<WebElement> issueButtons = driver.findElements(RelativeLocator.with(By.className("archive__year__link")).below(archiveHeader));

		final List<IssueDescriptor> issueDescriptors = issueButtons.stream()
			.filter((issueButton) -> !issueButton.getText().isEmpty())
			.map((issueButton) -> createIssue(issueButton))
			.filter((issue) -> issue != null)
			.collect(Collectors.toList());

		issueDescriptors.sort(new Comparator<IssueDescriptor>() // we need to sort for year and issue nr.:
		{
			@Override
			public int compare(IssueDescriptor issue1, IssueDescriptor issue2) {
				assert issue1 != null : "issue1 must not be null!";
				assert issue2 != null : "issue2 must not be null!";
				int res = issue2.jahrgang.compareTo(issue1.jahrgang); // inverse ordering (i.e. higher to lower)
				res = (res != 0 ? res : issue2.issueNr.compareTo(issue1.issueNr));
				log.trace("{}/{} - {}/{} --> {}", issue1.jahrgang, issue1, issue2.jahrgang, issue1, res);
				return res;
			}
		});

		log.info( "Found {} issues.", issueDescriptors.size());
		return issueDescriptors;
	}

	private IssueDescriptor createIssue(final WebElement issue) {
		final String buttonLabel = issue.getText();

		final Matcher matcher = buttonLabelPattern.matcher(buttonLabel);
		if (!matcher.find()) {
			log.error("'{}' did not match pattern '{}' -> unable to extract year and issue-nr from name: ignored - please download manually", buttonLabel, buttonLabelPattern);
			return null;
		}
		String issueNr = matcher.group(3);
		if (issueNr == null) {
			issueNr = matcher.group(4).trim();
		}
		final String jahrgang = matcher.group(5);

		log.debug("'{}' -> '{}' / '{}'", buttonLabel, jahrgang, issueNr);

		return new IssueDescriptor(issue, jahrgang, issueNr);
	}

	void loadMissingIssues(final List<IssueDescriptor> issueDescriptors) {
		try {
			// loading all displayed issues not already present on target folder:
			for (IssueDescriptor issue : issueDescriptors) {
				log.debug("issue: {}:", issue);
				if (!checkExists(issue)) {
					downloadIssue(issue);
				} else {
					log.info("--> no action for issue {}", issue);
				}
			}
		} catch (Exception e) {
			log.error("error loading", e);
		}
	}

	boolean checkExists(final IssueDescriptor issue) {
		final String path = replacePlaceHolders(targetPath != null ? targetPath : downloadPath, issue);
		log.debug("checking whether folder '{}' exists:", path);
		if (new File(path).exists()) {
			final String filename = replacePlaceHolders(issueFileName, issue);
			final String fullpath = path + (path.endsWith(File.separator) ? "" : File.separatorChar) + filename;
			issue.setFilename(filename);
			issue.setTargetFullPath(fullpath);
			log.info("checking whether file '{}' already exists:", fullpath);
			if (new File(fullpath).exists()) {
				log.info("already exists.");
				return true;
			} else {
				log.info("'{}' not found.", fullpath);
				return false;
			}
		} else {
			log.info("ignoring issues for {} - folder '{}' does not exist.", issue.getJahrgang(), path);
			return true;
		}
	}

	/** replace in template:
	 * %1: magazin-name prefix
	 * %2:  with jahrgang
	 * %3: last two digits of jahrgang
	 * %4: issue-nr.
	 * @param template
	 * @param issue
	 * @return
	 */
	private String replacePlaceHolders(final String template, final IssueDescriptor issue) {
		final String jahrgang = issue.getJahrgang();
		final String jahrgangLastDigits = jahrgang.substring(2);
		final String issueNr = issue.getIssueNr();
		return String.format(template, magazine.filenamePrefix, jahrgang, jahrgangLastDigits, issueNr.length() >= 2 ? issueNr : "0" + issueNr);
	}

	void downloadIssue(final IssueDescriptor issue) throws Exception {
		log.info("downloading '{}':", issue);
		log.info("Clicking '{}'", issue.button.getText());
		issue.button.click();

		log.info("waiting for the download link to appear:");
		final WebElement downloadlink = waitForAppearance("issue-download-link", 65);

		// remove existing prior file with same name:
		final String downloadLoc = replacePlaceHolders(downloadPath, issue);
		final String downloadFullPath = downloadLoc + (downloadLoc.endsWith(File.separator) ? "" : File.separator) + issue.getFilename();
		final File downloadFile = new File(downloadFullPath);
		if (downloadFile.exists()) {
			downloadFile.delete();
		}
		log.info("clicking '{}':", downloadlink.getText());
		downloadlink.click(); // Note: this immediately starts downloading the file to the download folder (i.e. without asking for a destination where to save it)!W
		log.info("downloading to '{}':", downloadFile);

		// wait until download completes:
		int nrWaits = 0;
		while (!downloadFile.exists() && !downloadFile.canRead() && nrWaits <= DownloadMaxWait) {
			log.info("waiting for download of '{}' to complete ({}):", downloadFile, nrWaits++);
			Thread.sleep(1000);
		}
		if (nrWaits > DownloadMaxWait) {
			throw new Exception(String.format("Download  did not complete in '%d' seconds - aborted.", nrWaits));
		} else {
			log.info("found '{}':", downloadFile.getAbsolutePath());
		}

		if (downloadFile.exists() && downloadFile.canRead()) {
			if (targetPath == null || targetPath.equals(downloadPath)) {
				log.debug("Downloaded file is already in target folder.");
			} else { // move the downloaded file to the target destination:
				final File targetFile = new File(issue.getTargetFullPath());
				// if target exists already: delete it:
				if (targetFile.exists()) {
					log.info("deleting prior existing file '{}':", targetFile);
					if (targetFile.delete()) {
						log.info("prior existing file deleted.:", targetFile);
					} else {
						log.warn("unabled to delete prior existing file '{}' - the following move will likely fail:", targetFile);
					}
				}
				log.info("moving the downloaded file '{}' to the target destination '{}':", downloadFile, targetFile);
				if (downloadFile.renameTo(targetFile)) {
					log.info("done.");
				} else {
					log.error("Failed to move the downloaded file '{}' to the target destination '{}' - file remains in download folder", downloadFile, targetFile);
				}
			}
		} else {
			log.error("downloaded file '{}' not found!", downloadFile);
		}
		driver.navigate().back(); // go back to the issue list page in preparation for possible further downloads)
	}

	void closeBrowser() {
		log.info("closeBrowser.");
		if (driver != null) { // terminate the browser.
			driver.quit();
			driver = null;
		}
	}

	WebElement waitForAppearance(final String className) throws Exception {
		return waitForAppearance(className, AppearanceDefaultWait);
	}
	WebElement waitForAppearance(final String className, final int waitMaxSeconds) throws Exception {
		return waitForAppearance(By.className(className), waitMaxSeconds);
	}

	WebElement waitForAppearance(final By by, final int waitMaxSeconds) throws Exception {
		log.info("waiting for appearance of element '{}'", by);
		List<WebElement> elems = null;
		WebElement expectedElem = null;
		int nrAttempts = 0;
		do {
			elems = driver.findElements(by);
			if (elems.size() > 0 && (expectedElem = elems.getFirst()).isDisplayed()/* && expectedElem.isEnabled()*/) {
				break;
			}
			if (nrAttempts++ >= waitMaxSeconds) {
				log.info("no element '" + by + "' found within " + waitMaxSeconds + " seconds");
				return null;
			}
			log.info("waiting ({})...", nrAttempts);
			Thread.sleep(1000);
		} while (true);
		return expectedElem;
	}

	private void processCommandLine(final CommandLine line, final Options options) throws Exception {
		for (Option opt: line.getOptions()) {
			log.debug("option {}: '{}'", (char)opt.getId(), opt.getValue());
			switch (opt.getId()) {
			case 'm':
				magazine = Magazine.valueOf(opt.getValue().toLowerCase());
				break;
			case 'd':
				this.downloadPath = opt.getValue().replace('/', File.separatorChar);
				if (this.downloadPath.endsWith("\"")) { // for some odd reason the trailing quote from the cmd-file makes in into the argument ||-(
					this.downloadPath = this.downloadPath.substring(0, this.downloadPath.length()-1);
				}
				break;
			case 't':
				this.targetPath = opt.getValue().replace('/', File.separatorChar);
				if (this.targetPath.endsWith("\"")) { // for some odd reason the trailing quote from the cmd-file makes in into the argument ||-(
					this.targetPath = this.targetPath.substring(0, this.targetPath.length()-1);
				}
				break;
			case 'u':
				this.usr = opt.getValue();
				break;
			case 'p':
				this.pwd = opt.getValue();
				break;
			default:
				log.error("Unexpected option: '{}' - ignored.");
				usage(options, -4);
			}
		}
		if (this.usr == null || this.pwd == null || line.getArgList().size() > 0) {
			usage(options, -5);
		}
	}

	private static void usage(final Options options, final int exitCode) {
		// automatically generate the help statement
		final HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(100, "java -jar <jar.file> { <options> }.\n\n", "options are:", options, "");
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	private static Options createOptions() {
		final Options options = new Options();
		options.addOption(new Option("m", "magazine", true, "magazine name [optional - default: '" + Magazine.ct + "']"));
		options.addOption(new Option("u", "username", true, "user-id for login to Heise Media [required]"));
		options.addOption(new Option("p", "password", true, "password for login to Heise Media [required]"));
		options.addOption(new Option("d", "download-folder", true, "download-folder [optional - default: '" + DefaultDownloadPath + "']"));
		options.addOption(new Option("t", "target-folder", true, "target-folder [optional - default: same as download-folder]"));
		return options;
	}

	public static void main(final String[] arguments) {
		Options options = null;
		try {
			options = createOptions();
		} catch (Exception exp) {
			System.err.println("Internal error initializing the program options: " + exp.getMessage());
			exp.printStackTrace();
			System.exit(-1);
		}
		CommandLine line = null;
		try {
			// create the parser
			final CommandLineParser parser = new DefaultParser();
			// parse the command line arguments:
			line = parser.parse(options, arguments);
		} catch (Exception exp) {
			System.err.println("Illegal or malformed option(s): " + exp.getMessage());
			usage(options, -2);
		}

		try {
			final Download_Heise downloader = new Download_Heise();
			downloader.processCommandLine(line, options);
			downloader.init();
			downloader.setUpBrowser();
			downloader.getRidOfCookieGarbage();
			final List<IssueDescriptor> issueDescriptors = downloader.loadListOfLastIssues();
			downloader.loadMissingIssues(issueDescriptors);
			downloader.closeBrowser();
		} catch (Throwable t) {
			System.err.println("error executing " + Download_Heise.class.getSimpleName());
			t.printStackTrace();
		}
	}
}