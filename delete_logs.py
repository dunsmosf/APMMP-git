from subprocess import *

#CHANGE THE SHELL COMMANDS HERE
#Obviously if you use platform-specific commands like windows/unix commands it won't work on everything
#But any adb commands should work
SHELL_COMMANDS = r"""
    
adb shell rm /sdcard/rocheLogcat.txt
adb shell rm /sdcard/cgmServiceLogcat.txt
adb shell rm /sdcard/pumpServiceLogcat.txt
adb shell rm /sdcard/diasServiceLogcat.txt
adb shell rm /sdcard/dexcomBtleLogcat.txt
adb shell rm /sdcard/tandemLogcat.txt
    
"""

for command in SHELL_COMMANDS.split('\n'):
	if (len(command) > 0) and not (command.startswith('::') or command.startswith('#')):
		print command
		print Popen(command, stdout=PIPE, shell=True).stdout.read()