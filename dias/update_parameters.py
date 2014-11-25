import os, sys, subprocess

STORAGE_DIR = '/mnt/sdcard'

def update_params(arg):
    if len(arg) < 2:
        print('Error: you must provide a file name')
        return False
    elif len(arg) > 2:
        print('Error: you can only provide one file name')
        return False
    file_name = arg[1]
    if not file_name[-4:] == '.xml':
        print('Error: you must provide an xml file name')
        return False
    if not file_name[:10] == 'parameters':
        print('Error: file name must start with \'parameters\'')
        return False

    cmd = 'adb shell cp "'+os.path.join(STORAGE_DIR, file_name)+'" "'+ os.path.join(STORAGE_DIR, 'parameters.xml') +'"'

    s = subprocess.check_output(cmd.split())

    if s.split('\r\n')[0]:
        print('Error: "'+file_name+'" does not exist or cannot be copied')
        return False
    else:
        print('Success: "'+file_name+'" content has been copied into "parameters.xml"')
        return True

update_params(sys.argv)