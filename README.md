# c't Downloader
This is a utility to regularly download all issues of the c't magazine (cf. https://www.heise.de/ct) in PDF format.

It uses Selenium (c.f. https://www.selenium.dev/) to start a Chrome-Browser instance, navigates to https://www.heise.de/select/ct/archiv and 
then selects all issues shown, verifies that each is already present in the target folder and - if missing - downloads it.

Thus, running this more frequent than the four issues listed of that page are dropping off to the right (i.e. at least every 8 weeks) this will make sure that you have all issues of c't downloaded to your destination folder in PDF format for your reading pleasure.

See the `downloadCT.cmd`-file how to run this via a Windows cmd file.

usage: Download_CT
 -d,--download-folder <arg>   download-folder  [optional - default: %HOME%\downloads-folder will be used]
 -p,--password <arg>          password for login to Heise Media [required]
 -t,--target-folder <arg>     target-folder [optional - default: leave file in the download-folder (above)]
 -u,--username <arg>          user-id for login to Heise Media [required]
 