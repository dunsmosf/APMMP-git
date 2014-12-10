from subprocess import *

#CHANGE THE SHELL COMMANDS HERE
#Obviously if you use platform-specific commands like windows/unix commands it won't work on everything
#But any adb commands should work
SHELL_COMMANDS = r"""

:: Configuration and parameters
:: ----------------------------------
adb shell am broadcast -a edu.virginia.dtc.intent.CUSTOM_ICON_REMOVE_ALL
adb push configurations.xml /mnt/sdcard/configurations.xml
adb push parameters.xml /mnt/sdcard/parameters.xml

:: Main DiAs Application
:: ----------------------------------
adb install "Ultron/build/outputs/apk/Ultron-debug.apk"

:: User Interface Application
:: ----------------------------------
adb install "Jarvis/build/outputs/apk/Jarvis-debug.apk"

:: Controllers
::----------------------------------

:: Drivers
::----------------------------------


"""

for command in SHELL_COMMANDS.split('\n'):
	if (len(command) > 0) and not (command.startswith('::') or command.startswith('#')):
		print command
		print Popen(command, stdout=PIPE, shell=True).stdout.read()