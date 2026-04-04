@echo off
rem Copyright © 2024-2026 by Michael Moser / 17732576+mmoser18@users.noreply.github.com
rem Released under GPL V3 or later
echo running c't downloader:

set prj=%HOME%\Documents\eclipse\workspace\heise_download
rem target folder:
set tgt=%prj%\target
rem program executable location:
set jar=%tgt%\heise_download-2.2.0.jar

rem copy over the log-config:
copy "%prj%\src\main\resources\*.xml" "%tgt%"

cd %tgt%
rem Note - the "file:" is essential so that log4j can find its config:
set log4j.configurationFile=file:log4j2.xml

rem Supported place-holders for the target-folder and download-folder strings are:
rem %1$s: jahrgang, %2$s: last two digits of jahrgang, %3$s: issue-nr.
rem with short options:
rem java -jar "%jar%" -u "<username here>" -p "<password here>" -t "<target folder path here>"
rem with spelled-out options:
java -jar "%jar%" --username "<username here>" --password "<password here>" --target-folder "<target folder path here>"
pause
log