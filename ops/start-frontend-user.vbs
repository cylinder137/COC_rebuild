' coc-platform : launch ops\start-frontend-user.cmd detached (hidden, survives the caller session)
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(WScript.ScriptFullName)
Set ws = CreateObject("WScript.Shell")
ws.CurrentDirectory = dir
ws.Run "cmd.exe /c """ & dir & "\start-frontend-user.cmd""", 0, False
