from subprocess import *

#CHANGE THE SHELL COMMANDS HERE
#Obviously if you use platform-specific commands like windows/unix commands it won't work on everything
#But any adb commands should work
SHELL_COMMANDS = r"""

::CONFIG AND PARAMETER PUSH
::-------------------------------------------------------------------
adb shell am broadcast -a edu.virginia.dtc.intent.CUSTOM_ICON_REMOVE_ALL

adb push configurations.xml /mnt/sdcard/configurations.xml
adb push parameters.xml /mnt/sdcard/parameters.xml

::UTILITIES/REQUIRED
::-------------------------------------------------------------------
adb install "dias/supervisor/bin/supervisor.apk"
adb install "dias/biometricsContentProvider/bin/biometricsContentProvider.apk"
adb install "dias/CgmService/bin/CgmService.apk"
adb install "dias/DiAsService/bin/DiAsService.apk"
adb install "dias/DiAsSetupNew/bin/DiAsSetupNew.apk"
adb install "dias/DiAsUI/bin/DiAsUI.apk"
adb install "dias/networkService/bin/networkService.apk"
adb install "dias/PumpService/bin/PumpService.apk"

::SHELLS/CONTROLLERS
::-------------------------------------------------------------------
adb install "replaceable_modules/ConstraintServiceShell/bin/ConstraintServiceShell.apk"
::adb install "replaceable_modules/ExerciseServiceShell/bin/ExerciseServiceShell.apk"
adb install "dias/ExerciseService/bin/ExerciseService.apk"

adb install "replaceable_modules/APCserviceShell/bin/APCserviceShell.apk"
::adb install "replaceable_modules/APCserviceShellQuad/bin/APCserviceShellQuad.apk"
adb install "replaceable_modules/BRMserviceShell/bin/BRMserviceShell.apk"
::adb install "replaceable_modules/SSMservicePassThrough/bin/SSMservicePassThrough.apk"
adb install "replaceable_modules/SSMserviceShell/bin/SSMserviceShell.apk"

adb install "replaceable_modules/MCMservice/bin/MCMservice.apk"

::DRIVERS
::-------------------------------------------------------------------
adb install "dias/standaloneDriver/bin/standaloneDriver.apk"
adb install "dias/RocheDriver/bin/RocheDriver.apk"
::adb install "dias/BTLE_G4/bin/BTLE_G4.apk"
::adb install "dias/BTLE_Tandem/bin/BTLE_Tandem.apk"
adb install "dias/BTLE_Share/bin/BTLE_Share.apk"

"""

for command in SHELL_COMMANDS.split('\n'):
	if (len(command) > 0) and not (command.startswith('::') or command.startswith('#')):
		print command
		print Popen(command, stdout=PIPE, shell=True).stdout.read()