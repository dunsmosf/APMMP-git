from subprocess import *

#CHANGE THE SHELL COMMANDS HERE
#Obviously if you use platform-specific commands like windows/unix commands it won't work on everything
#But any adb commands should work
SHELL_COMMANDS = r"""

::Required packages
::---------------------------------------------------------
adb uninstall edu.virginia.dtc.biometricsContentProvider
adb uninstall edu.virginia.dtc.CgmService
adb uninstall edu.virginia.dtc.DiAsService
adb uninstall edu.virginia.dtc.DiAsSetup
adb uninstall edu.virginia.dtc.DiAsUI
adb uninstall edu.virginia.dtc.networkService
adb uninstall edu.virginia.dtc.PumpService
adb uninstall edu.virginia.dtc.supervisor
adb uninstall edu.virginia.dtc.ConstraintService

::Deprecated packages
::---------------------------------------------------------
adb uninstall edu.virginia.dtc.HMSservice
adb uninstall edu.virginia.dtc.safetyService
adb uninstall edu.virginia.dtc.MealService

::Driver packages
::---------------------------------------------------------
adb uninstall edu.virginia.dtc.USBDexcomLocalDriver
adb uninstall edu.virginia.dtc.standaloneDriver
adb uninstall edu.virginia.dtc.GlassDriver
adb uninstall edu.virginia.dtc.TandemDriver
adb uninstall edu.virginia.dtc.BTLEDriver
adb uninstall edu.virginia.dtc.USBiDexDriver
adb uninstall edu.virginia.dtc.DexcomG5Driver
adb uninstall com.dexcom.service
adb uninstall edu.virginia.dtc.DexcomBTRelayDriver
adb uninstall edu.virginia.dtc.RocheDriver
adb uninstall edu.virginia.dtc.BTLE_Tandem
adb uninstall edu.virginia.dtc.BTLE_G4
adb uninstall edu.virginia.dtc.HR_Driver
adb uninstall edu.virginia.dtc.ExerciseService

::Service packages
::---------------------------------------------------------
adb uninstall edu.virginia.dtc.BRMservice
adb uninstall edu.virginia.dtc.SSMservice
adb uninstall edu.virginia.dtc.MealActivity
adb uninstall edu.virginia.dtc.APCservice
adb uninstall edu.virginia.dtc.MCMservice

::pause
"""

for command in SHELL_COMMANDS.split('\n'):
	if (len(command) > 0) and not (command.startswith('::') or command.startswith('#')):
		print command
		print Popen(command, stdout=PIPE, shell=True).stdout.read()