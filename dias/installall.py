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
adb install "supervisor/bin/supervisor.apk"
adb install "biometricsContentProvider/bin/biometricsContentProvider.apk"
adb install "CgmService/bin/CgmService.apk"
adb install "DiAsService/bin/DiAsService.apk"
adb install "DiAsSetupNew/bin/DiAsSetupNew.apk"
adb install "DiAsUI/bin/DiAsUI.apk"
adb install "networkService/bin/networkService.apk"
adb install "PumpService/bin/PumpService.apk"

::SHELLS/CONTROLLERS
::-------------------------------------------------------------------
::adb install "ConstraintServiceShell/bin/ConstraintServiceShell.apk"
::adb install "ExerciseServiceShell/bin/ExerciseServiceShell.apk"

::adb install "../controllers/USS/SSMservice/bin/SSMservice.apk"
::adb install "SSMservicePassThrough/bin/SSMservicePassThrough.apk"
::adb install "../controllers/USS/BRMservice/bin/BRMservice.apk"
::adb install "../controllers/HMS/HMSservice/bin/HMSservice.apk"

adb install "APCserviceShell/bin/APCserviceShell.apk"
adb install "BRMserviceShell/bin/BRMserviceShell.apk"
adb install "SSMserviceShell/bin/SSMserviceShell.apk"

adb install "MCMservice/bin/MCMservice.apk"

::DRIVERS
::-------------------------------------------------------------------
adb install "standaloneDriver/bin/standaloneDriver.apk"
::adb install "RocheDriver/bin/RocheDriver.apk"
::adb install "BTLE_G4/bin/BTLE_G4.apk"
::adb install "BTLE_Tandem/bin/BTLE_Tandem.apk"
::adb install "BTLE_Share/bin/BTLE_Share.apk"

"""

for command in SHELL_COMMANDS.split('\n'):
	if (len(command) > 0) and not (command.startswith('::') or command.startswith('#')):
		print command
		print Popen(command, stdout=PIPE, shell=True).stdout.read()