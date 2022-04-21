Set oShell = WScript.CreateObject("WScript.Shell") 
Set fSO = CreateObject("Scripting.FileSystemObject")

On Error Resume Next
oShell.RegRead("HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\")
If err.number <> 0 Then
    WScript.echo "Java not found." & vbCrLf & "To continue - install at least JDK-8."
    WScript.Quit
End If

helpText = "A program ""repairs"" LNG key on OscarSM660L keyboard." &_
		vbCrLf & "While user login it installs a hook on keyboard." &_
		vbCrLf & "The hook replaces RCtrl+RShift whith LAlt+LShift."

linkFile = oShell.SpecialFolders("Startup") + "\OscarSM660L.lnk"

startText = "Autostart link created:" & vbCrLf & vbCrLf
startText = startText + linkFile & vbCrLf & vbCrLf & "Start it now?" &_
		vbCrLf & vbCrLf & "Note:" & vbCrLf & "1) OscarSM660L-hook runs" &_
		" in a single copy and won't work if Task Manager in focus." &_
		vbCrLf & "2) To stop it kill java.exe process or press LNG key + Q."

If fSO.FileExists(linkFile) Then
    If MsgBox("Autostart link already exists:" & vbCrLf & vbCrLf + linkFile + vbCrLf & vbCrLf & "Remove it?", 65, "Managing OscarSM660L-hook autostart:") = 1 Then
	fSO.DeleteFile linkFile
    End If
Else
    If MsgBox(helpText, 65, "Managing OscarSM660L-hook autostart:") = 1 Then
	With oShell.CreateShortcut(linkFile)
	    scriptDir = fSO.GetParentFolderName(WScript.ScriptFullName)
    	    .TargetPath = """" + scriptDir + "\OscarSM660L.vbs"""
	    .WorkingDirectory = scriptDir
    	    .Save
	End With 
        If MsgBox(startText, 65, "Managing OscarSM660L-hook autostart:") = 1 Then
	    oShell.Run "OscarSM660L.vbs"
        End If
     End If
End If



