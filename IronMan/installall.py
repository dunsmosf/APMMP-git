from subprocess import *

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
adb install "ApcShell/build/outputs/apk/ApcShell-debug.apk"
adb install "SsmShell/build/outputs/apk/SsmShell-debug.apk"

:: Drivers
::----------------------------------
adb install "SimulationDriver/build/outputs/apk/SimulationDriver-debug.apk"

"""

for command in SHELL_COMMANDS.split('\n'):
	if (len(command) > 0) and not (command.startswith('::') or command.startswith('#')):
		print command
		print Popen(command, stdout=PIPE, shell=True).stdout.read()