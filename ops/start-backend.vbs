' coc-platform : launch ops\start-backend.cmd detached (hidden, survives the caller session)
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(WScript.ScriptFullName)
Set ws = CreateObject("WScript.Shell")
ws.CurrentDirectory = dir
ws.Run "cmd.exe /c """ & dir & "\start-backend.cmd""", 0, False
