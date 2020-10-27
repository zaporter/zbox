# ZBox

Help for ZBox:
ping:
    say pong
remove:
    Removes box from local system 
    Format: remove [options]
    Example: remove -b test1box [options]
    Options: (One of two first required)
    -p, -path [relative file path]: specifies root folder of box
    -b, -box [box name]: specifies name of box
    -f, -files: Removes all files present in the box
create:
    Link a pre-existing box to a folder or create a new box if none exist.
    Format: create [box name] [folder path]
    Example: create test1box /home/joe/Documents
    Expected outcome: Link the public box test1box
    to /home/joe/Documents and upload all current files while 
    downloading those that are already there.
delete:
    Deletes box from S3. 
    Format: delete [box name]
    This DOES NOT remove the box from the local filesystem
    Warning: This will require a password and should be done carefully
ls:
    List files of a certain type.
    Format: ls [box name] [options]
    Example: ls test1box -a -h
    If no box is specified, all boxes will be listed instead
    Options: 
    -s, -synced: lists all synced files
    -a, -all: lists all files in the box
    -d, -deleted: lists all recently deleted files
getOutput:
    Hooks up an output-stream of current log data.
    Format: getOutput
    Warning: This must be exited with ctrl+C
timingInfo:
    Prints timing info for current session
    Format: timingInfo
sync:
    Force a sync of a box or of all boxes.
    Format: sync [options]
    OR Format: sync
    If no argument is provided, all boxes are synced.
    Options: (One of two required for single sync)
    -p, -path [relative file path]: specifies root folder of box
    -b, -box [box name]: specifies name of box
start:
    
shutdown:
    Shutdown the box:		Options:		-f, -force: force shutdown now. (Otherwise will finish sync in progress if any then shutdown)
     -t, -thread: only shut down the sync thread

