from subprocess import *

#CHANGE THE SHELL COMMANDS HERE
#Obviously if you use platform-specific commands like windows/unix commands it won't work on everything
#But any adb commands should work
SHELL_COMMANDS = r"""
    
mkdir zLogcatTraces
adb pull /sdcard/rocheLogcat.txt $PWD/zLogcatTraces/
adb pull /sdcard/cgmServiceLogcat.txt $PWD/zLogcatTraces/
adb pull /sdcard/pumpServiceLogcat.txt $PWD/zLogcatTraces/
adb pull /sdcard/diasServiceLogcat.txt $PWD/zLogcatTraces/
adb pull /sdcard/dexcomBtleLogcat.txt $PWD/zLogcatTraces/
adb pull /sdcard/tandemLogcat.txt $PWD/zLogcatTraces

"""

for command in SHELL_COMMANDS.split('\n'):
	if (len(command) > 0) and not (command.startswith('::') or command.startswith('#')):
		print command
		print Popen(command, stdout=PIPE, shell=True).stdout.read()