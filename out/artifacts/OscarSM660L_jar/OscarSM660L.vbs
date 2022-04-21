Set oShell = WScript.CreateObject("WScript.Shell")
oShell.Run "cmd /c java -Xmx64m -jar OscarSM660L.jar " + WScript.ScriptName, 0, True