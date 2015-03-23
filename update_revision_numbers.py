'''
    2014-03-27: 'update_revision_numbers.py'
    
    This script scans the content of the working copy to detect any change in the projects using an "AndroidManifest.xml" file.
    If changes are detected in a project, the SVN keyword $Rev$ is reset in the manifest file of this project.
    This revision number will be automatically updated by SVN upon Commit and Update.
    
'''

import os, re
import shutil, subprocess

MANIFEST_FILE = "AndroidManifest.xml"

directories = os.listdir('.')
dir_to_update = []

for directory in directories:
    if os.path.isdir(directory) and MANIFEST_FILE in os.listdir(directory):
        p = subprocess.Popen("svn stat "+directory, stdout=subprocess.PIPE, shell=True)
        (output, err) = p.communicate()
        if not output == '':
            dir_to_update.append(directory)

if dir_to_update:
    print "Updating revision numbers for the following projects:"
    for dir in dir_to_update:
        print dir
        oldfile = os.path.join(dir, MANIFEST_FILE)
        tempfile = os.path.join(dir, "~" + MANIFEST_FILE)
        shutil.move(oldfile, tempfile)
        fr = open(tempfile, "r")
        fw = open(oldfile, "w")
        for line in fr:
            match = re.search("\"\$Rev.*?\$.*?\"", line)
            if match != None:
                rev = line[match.start():match.end()]
                if rev[-2:] == '*"':
                    fw.write(re.sub("\"\$Rev.*?\$\*\"", "\"$Rev$\"", line))
                else:
                    fw.write(re.sub("\"\$Rev.*?\$\"", "\"$Rev$*\"", line))
            else:
                fw.write(line)
        fw.close()
        fr.close()
        os.remove(tempfile)
    print "'Commit' the changes you made to these projects (including the Manifest) and run 'Update' to have the revision numbers updated in your working copy."
else:
    print "No changes in projects, no revision number to update in Manifests files."