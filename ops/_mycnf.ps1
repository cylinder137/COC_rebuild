# coc-platform ops helper: build a temporary MySQL option file (my.cnf)
# The DB password is read from the local secret file and written ONLY into a
# temporary file under %TEMP%. It is never printed and never passed on a
# command line, and never stored inside the repository.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File _mycnf.ps1 -Out "%TEMP%\coc-my.cnf"
param(
    [Parameter(Mandatory = $true)][string]$Out,
    [string]$PasswordFile = 'C:\Users\Administrator\.tinker\db_password.txt',
    [string]$User = 'root',
    [string]$Host_ = '127.0.0.1',
    [int]$Port = 3306
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $PasswordFile)) {
    Write-Error "[coc-ops] password file not found: $PasswordFile"
    exit 2
}

$pw = [System.IO.File]::ReadAllText($PasswordFile).Trim()
# tolerate a file that wraps the password in single or double quotes
if ($pw.Length -ge 2) {
    if (($pw.StartsWith('"') -and $pw.EndsWith('"')) -or ($pw.StartsWith("'") -and $pw.EndsWith("'"))) {
        $pw = $pw.Substring(1, $pw.Length - 2)
    }
}
if ([string]::IsNullOrEmpty($pw)) {
    Write-Error "[coc-ops] password file is empty: $PasswordFile"
    exit 3
}

$lines = @(
    '[client]'
    "user=$User"
    "password=`"$pw`""
    "host=$Host_"
    "port=$Port"
    'default-character-set=utf8mb4'
    'connect-timeout=10'
)
# UTF-8 without BOM (MySQL does not like a BOM in option files)
$enc = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($Out, (($lines -join "`n") + "`n"), $enc)
exit 0
