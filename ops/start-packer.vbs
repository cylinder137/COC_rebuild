' coc-platform : launch ops\start-packer.cmd detached (hidden, survives the caller session)
' Use:  wscript.exe ops\start-packer.vbs
' NEVER use Start-Process from a shell session: the child dies when that session is reaped.
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(WScript.ScriptFullName)
Set ws = CreateObject("WScript.Shell")
ws.CurrentDirectory = dir
ws.Run "cmd.exe /c """ & dir & "\start-packer.cmd""", 0, False
