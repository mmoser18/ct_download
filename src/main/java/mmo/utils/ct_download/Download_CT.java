/**
 * Copyright © 2024-2025 by Michael Moser
 * Released under GPL V3 or later
 *
 * @author mmo / Michael Moser / 17732576+mmoser18@users.noreply.github.com
 */

package mmo.utils.ct_download;

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
public class Download_CT
{
	private final static String baseUrl = "https://www.heise.de/select/ct/archiv";
	private final static String ButtonLabelPattern = "c't (((\\d{1,2})/)|(Jahresrückblick ))(\\d{4})";
	private final static Pattern buttonLabelPattern = Pattern.compile(ButtonLabelPattern);
	private static final String IssueFileName = "ct.%2$s.%3$s.pdf"; // %1: jahrgang, %2: last two digits of jahrgang, %3: issue-nr.
	private final static int DownloadMaxWait = 200; // [seconds] max. completion wait time before a download is considered failed
	private final static int AppearanceDefaultWait = 10; // [seconds]
	private final static String DefaultDownloadPath = (System.getProperty("os.name").startsWith("Windows") 
	                                                  ? System.getProperty("user.home", "U:") // assuming "U:" points to user's home directory
	                                                  : "~") // for *ix and Mac
	                                                  + File.separator + "Downloads";

	private String downloadPath = DefaultDownloadPath;
	private String targetPath;
	private String usr;
	private String pwd;
	
	private WebDriver driver;
	
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
		log.info("navigating to '" + baseUrl + "':");
		driver.get(baseUrl);
	}
	
	List<IssueDescriptor> loadListOfLastIssues() throws Exception {
		// Ensure that the user has reached https://www.heise.de/select/ct/archiv:
		WebElement textOnHomePage = driver.findElement(By.xpath("//h1[contains(text(),\"Artikel-Archiv c't\")]"));
		if (!textOnHomePage.isDisplayed()) {
			throw new Exception("The user hasn't arrived at the Artikel-Archiv c't.");
		}

		WebElement anmeldenButton = driver.findElement(By.xpath("//span[contains(.,'Anmelden')]"));
		log.debug("loginButton=" + anmeldenButton);
		if (anmeldenButton.isDisplayed()) { // we are not logged-in, yet.
			log.info("\"Anmelden\" is displayed - logging in:");
			anmeldenButton.click();
			// filling out the login form:			
			WebElement loginUser = waitForAppearance(By.id("login-user"), 3); // give the site a few seconds to display the login-form...
			WebElement loginPassword = driver.findElement(By.id("login-password"));
			WebElement loginSubmit = driver.findElement(By.name("rm_login"));
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

		WebElement archiveHeader = waitForAppearance("archive__header");		
		List<WebElement> issueButtons = driver.findElements(RelativeLocator.with(By.className("archive__year__link")).below(archiveHeader));
		
		final List<IssueDescriptor> issueDescriptors = issueButtons.stream()
				.filter(issueButton -> !issueButton.getText().isEmpty())
				.map((issueButton) -> createIssue(issueButton))
				.collect(Collectors.toList());
		
		issueDescriptors.sort(new Comparator<IssueDescriptor>() // we need to sort for year and issue nr.:
		{
			@Override
			public int compare(IssueDescriptor issue1, IssueDescriptor issue2) {
				int res = issue2.jahrgang.compareTo(issue1.jahrgang); // inverse ordering (i.e. higher to lower)
				res = (res != 0 ? res : issue2.issueNr.compareTo(issue1.issueNr));
				log.trace("{}/{} - {}/{} --> {}", issue1.jahrgang, issue1.issueNr, issue2.jahrgang, issue2.issueNr, res);
				return res;
			}
		});

		log.info( "Found {} issues.", issueDescriptors.size());
		return issueDescriptors;
	}

	private IssueDescriptor createIssue(WebElement issue) {
		String buttonLabel = issue.getText();
		
		Matcher matcher = buttonLabelPattern.matcher(buttonLabel);
		if (!matcher.find()) {
			log.error("'{}' did not match pattern '{}'", buttonLabel, ButtonLabelPattern);
			return null;
		}
		String issueNr = matcher.group(3);
		if (issueNr == null) {
			issueNr = matcher.group(4).trim(); 
		}
		String jahrgang = matcher.group(5);
		
		log.debug("'{}' -> '{}' / '{}'", buttonLabel, jahrgang, issueNr);

		return new IssueDescriptor(issue, jahrgang, issueNr);
	}

	void loadMissingIssues(List<IssueDescriptor> issueDescriptors) {
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

	boolean checkExists(IssueDescriptor issue) {
		String path = replacePlaceHolders(targetPath != null ? targetPath : downloadPath, issue);
		if (new File(path).exists()) {
			String filename = replacePlaceHolders(IssueFileName, issue);
			String fullpath = path + (path.endsWith(File.separator) ? "" : File.separatorChar) + filename;
			issue.setFilename(filename);
			issue.setTargetFullPath(fullpath);
			log.info("checking for '{}':", fullpath);
			if (!new File(fullpath).exists()) {
				log.info("'{}' not found.", fullpath);
				return false;			
			} else {
				log.info("already exists.");
				return true;
			}
		} else {
			log.info("ignoring issues for {}.", issue.getJahrgang());
			return true;
		}
	}

	/** replace in template:
	 * %1 with jahrgang
	 * %2: last two digits of jahrgang
	 * %3: issue-nr.
	 * @param template
	 * @param issue
	 * @return
	 */
	private String replacePlaceHolders(String template, IssueDescriptor issue) {			
		String jahrgang = issue.getJahrgang();
		String jahrgangLastDigits = jahrgang.substring(2);
		String issueNr = issue.getIssueNr();
		return String.format(template, jahrgang, jahrgangLastDigits, issueNr.length() >= 2 ? issueNr : "0" + issueNr);
	}

	void downloadIssue(IssueDescriptor issue) throws Exception {
		log.info("downloading '{}':", issue);
		log.info("Clicking '{}'", issue.button.getText());
		issue.button.click();
		
		log.info("waiting for the download link to appear:");
		WebElement downloadlink = waitForAppearance("issue-download-link", 65);

		// remove existing prior file with same name:
		String downloadLoc = replacePlaceHolders(downloadPath, issue);
		String downloadFullPath = downloadLoc + (downloadLoc.endsWith(File.separator) ? "" : File.separator) + issue.getFilename();
		File downloadFile = new File(downloadFullPath);
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
				File targetFile = new File(issue.getTargetFullPath());
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
	
	WebElement waitForAppearance(String className) throws Exception {
		return waitForAppearance(className, AppearanceDefaultWait);
	}
	WebElement waitForAppearance(String className, int waitMaxSeconds) throws Exception {
		return waitForAppearance(By.className(className), waitMaxSeconds);
	}
	
	WebElement waitForAppearance(By by, int waitMaxSeconds) throws Exception {
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

	private void processCommandLine(CommandLine line, Options options) throws Exception {
		for (Option opt: line.getOptions()) {
			log.debug("option {}: '{}'", (char)opt.getId(), opt.getValue());
			switch (opt.getId()) {
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

	private static void usage(Options options, int exitCode) {
		// automatically generate the help statement
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(100, "java -jar <jar.file> { <options> }.\n\n", "options are:", options, "");
		if (exitCode != 0) System.exit(exitCode);
	}
	
	private static Options createOptions() {
		final Options options = new Options();
		options.addOption(new Option("u", "username", true, "user-id for login to Heise Media [required]"));
		options.addOption(new Option("p", "password", true, "password for login to Heise Media [required]"));
		options.addOption(new Option("d", "download-folder", true, "download-folder [optional - default: '" + DefaultDownloadPath + "']"));
		options.addOption(new Option("t", "target-folder", true, "target-folder [optional - default: same as download-folder]"));
		return options;
	}	

	public static void main(String[] arguments) {
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
			CommandLineParser parser = new DefaultParser();
			// parse the command line arguments:
			line = parser.parse(options, arguments);
		} catch (Exception exp) {
			System.err.println("Illegal or malformed option(s): " + exp.getMessage());
			usage(options, -2);
		}
		
		try {
			Download_CT downloader = new Download_CT();
			downloader.processCommandLine(line, options);
			downloader.setUpBrowser();	
			List<IssueDescriptor> issueDescriptors = downloader.loadListOfLastIssues();
			downloader.loadMissingIssues(issueDescriptors);
			downloader.closeBrowser();
		} catch (Throwable t) {
			System.err.println("error executing " + Download_CT.class.getSimpleName());
			t.printStackTrace();
		}
	}
}