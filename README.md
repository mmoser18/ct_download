# c't Downloader
This is a utility to regularly download all issues of the [c't magazine](https://www.heise.de/ct) in PDF format.

It uses [Selenium](https://www.selenium.dev/) to start a Chrome-Browser instance, navigates to 
[https://www.heise.de/select/ct/archiv](https://www.heise.de/select/ct/archiv) and then selects 
all issues shown, verifies that each is already present in the target folder and - if missing - downloads it.

Thus, running this more frequent than the four issues listed of that page are dropping off to the right (i.e. at least every 8 weeks) this will make sure that you have all issues of c't downloaded to your destination folder in PDF format for your reading pleasure.

Copyright © 2024-2025 by Michael Moser / 17732576+mmoser18@users.noreply.github.com

## Setup:
The basic command-line is 
`java -jar <jar-file> -u <userId> -p <password> <further options here...>`

See the `downloadCT.cmd`-file for an example how to run this via a Windows command-file
(Note: you will have to enter your user-id and password into that file to get going...).

```
usage: Download_CT
 -u,--username <arg>          user-id for login to Heise Media [required]
 -p,--password <arg>          password for login to Heise Media [required]
 -d,--download-folder <arg>   download-folder  [optional - default: %HOME%\downloads-folder will be used]
 -t,--target-folder <arg>     target-folder [optional - default: leave file in the download-folder (above)]
```

## Building the application

Just run the "typical" Maven install, i.e. `mvn clean install` to build the application. The generated run-able .jar can then be found in the project's target subdirectory as `<application>-<version>.jar`

__A remark re. the build process:__

For some reason the generation of an all-in-one .jar (i.e. a .jar-file that includes all application dependencies - which I generate to make it simpler to start and execute the jar instead of having to deal with dozens of additional .jars and their relative paths) first generates a tiny .jar which contains only the application's own class files. The presence of this .jar causes the generation of the all-in-one .jar later in the build to fail. So I added build steps to generate the second .jar under a different name, then to move the first .jar out of the way (by renaming it as `<jar-name>.jar.original`) and then renaming the all-in-one-jar as `<application>-<version>.jar`. If someone knows why the generation of that first .jar happens and has an idea or trick how to skip its generation in the first place, please let me know! 
