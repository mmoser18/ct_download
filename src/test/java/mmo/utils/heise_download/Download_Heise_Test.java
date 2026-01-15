/**
 * Copyright © 2024-2025 by Michael Moser
 * Released under GPL V3 or later
 *
 * @author mmo / Michael Moser / 17732576+mmoser18@users.noreply.github.com
 */

package mmo.utils.heise_download;

import org.testng.annotations.*;

import mmo.utils.heise_download.Download_Heise;


public class Download_Heise_Test 
{
	@BeforeMethod
	public void setUpBrowser() {
		// ...
	}

	@Test
	public void loadLastCT() {
		// you can enter your actual credentials here for testing. The defaults just 
		// cause the browser to open but will not allow to do any actual download 
		Download_Heise.main(new String[] {"-m", "ct", 
		                                  "-u", "yourUserIdHere", 
		                                  "-p", "yourPwdHere",
		                                  "-t", "C:\\temp\\" 
		                                 });
	}

	@AfterMethod
	public void closeBrowser() {
		// ...
	}
}