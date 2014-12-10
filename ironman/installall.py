from subprocess import *

#CHANGE THE SHELL COMMANDS HERE
#Obviously if you use platform-specific commands like windows/unix commands it won't work on everything
#But any adb commands should work
SHELL_COMMANDS = r"""

::CONFIG AND PARAMETER PUSH
::-------------------------------------------------------------------
adb push configurations.xml /mnt/sdcard/configurations.xml
adb push parameters.xml /mnt/sdcard/parameters.xml

::UTILITIES/REQUIRED
::-------------------------------------------------------------------
::adb install "Ultron/bin/Ultron.apk"
::adb install "Jarvis/bin/Jarvis.apk"

::CONTROLLERS
::-------------------------------------------------------------------
adb install "HMSservice/bin/HMSservice.apk"
adb install "BRMservice/bin/BRMservice.apk"
adb install "SSMservice/bin/SSMservice.apk"
adb install "MealActivity/bin/MealActivity.apk"
adb install "MCMservice/bin/MCMservice.apk"

::DRIVERS
::-------------------------------------------------------------------
adb install "standaloneDriver/bin/standaloneDriver.apk"
adb install "RocheDriver/bin/RocheDriver.apk"
adb install "BTLE_Share/bin/BTLE_Share.apk"

"""

for command in SHELL_COMMANDS.split('\n'):
	if (len(command) > 0) and not (command.startswith('::') or command.startswith('#')):
		print command
		print Popen(command, stdout=PIPE, shell=True).stdout.read()