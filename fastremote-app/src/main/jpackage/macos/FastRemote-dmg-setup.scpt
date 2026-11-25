with timeout of 600 seconds
tell application "Finder"
  set theDisk to a reference to (disks whose URL = "DEPLOY_VOLUME_URL")
  open theDisk

  -- Create and persist the functional drag target before touching presentation
  -- metadata. Closing and reopening prevents alias creation from resetting the
  -- icon-view options that Finder is about to store in .DS_Store.
  make new alias file at POSIX file "DEPLOY_VOLUME_PATH" to POSIX file "DEPLOY_INSTALL_LOCATION" with properties {name:"DEPLOY_INSTALL_LOCATION_DISPLAY_NAME"}
  update theDisk without registering applications
  delay 1
  try
    close (get window of theDisk)
  end try
  delay 1
  open theDisk

  set layoutApplied to false
  set layoutFailure to "Finder window did not become ready"
  repeat with attemptNumber from 1 to 10
    try
      delay 1
      set theWindow to a reference to (container window of disks whose URL = "DEPLOY_VOLUME_URL")
      set current view of theWindow to icon view
      set toolbar visible of theWindow to false
      set statusbar visible of theWindow to false
      set the bounds of theWindow to {400, 100, 920, 440}

      set theViewOptions to a reference to the icon view options of theWindow
      set arrangement of theViewOptions to not arranged
      set icon size of theViewOptions to 128
      set background picture of theViewOptions to POSIX file "DEPLOY_BG_FILE"

      set allTheFiles to the name of every item of theWindow
      set xpos to 120
      set ypos to 290
      set i to 1
      set j to 0
      repeat with theFile in allTheFiles
        set theFilePath to POSIX path of theFile
        set appFilePath to POSIX path of "/DEPLOY_TARGET"
        if theFilePath ends with "DEPLOY_INSTALL_LOCATION_DISPLAY_NAME" then
          set position of item theFile of theWindow to {390, 130}
        else if theFilePath ends with appFilePath then
          set position of item theFile of theWindow to {120, 130}
        else
          set position of item theFile of theWindow to {xpos, ypos}
          set xpos to xpos + 135
          if (i mod 3) is equal to 0 then
            set i to 1
            set ypos to ypos + 150
            set xpos to 120
          else
            set i to i + 1
          end if
          set j to j + 1
        end if
      end repeat

      if j is not equal to 0 then
        set icon size of theViewOptions to 96
      end if
      if j is greater than 0 and j is less than or equal to 3 then
        set the bounds of theWindow to {400, 100, 920, 525}
      end if
      if j is greater than 3 then
        set the bounds of theWindow to {400, 100, 920, 673}
      end if

      update theDisk without registering applications
      delay 5
      set layoutApplied to true
      close (get window of theDisk)
      exit repeat
    on error errorMessage number errorNumber
      set layoutFailure to errorMessage & " (" & errorNumber & ")"
      try
        close (get window of theDisk)
      end try
      delay 1
      open theDisk
    end try
  end repeat

  if layoutApplied is false then
    error "Unable to apply FastRemote DMG Finder layout: " & layoutFailure
  end if
end tell
end timeout
