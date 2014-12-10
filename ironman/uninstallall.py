from subprocess import *

#CHANGE THE SHELL COMMANDS HERE
#Obviously if you use platform-specific commands like windows/unix commands it won't work on everything
#But any adb commands should work
SHELL_COMMANDS = r"""

::Required packages
::---------------------------------------------------------
adb uninstall edu.virginia.dtc.DiAsService
adb uninstall edu.virginia.dtc.DiAsUI
adb uninstall edu.virginia.dtc.ConstraintService

::Driver packages
::---------------------------------------------------------
adb uninstall edu.virginia.dtc.standaloneDriver
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