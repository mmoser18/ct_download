echo running c't downloader:
set loc=%HOME%\Documents\eclipse\workspace\ct_download\target\
set jar=%loc%\ct_download-1.0.0.jar
cd %loc%
rem Supported place-holders for the target-folder and download-folder strings are:
rem %1$s: jahrgang, %2$s: last two digits of jahrgang, %3$s: issue-nr.
rem with short options:
rem java -jar "%jar%" -u "<username here>" -p "<password here>" -t "<target folder path here>"
rem with spelled-out options:
java -jar "%jar%" --username "<username here>" --password "<password here>" --target-folder "<target folder path here>"
pause
