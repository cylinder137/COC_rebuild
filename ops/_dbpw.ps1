# coc-platform ops helper: print the local MySQL password on stdout
# Used by start-backend.cmd to populate COC_DB_PASSWORD for the Spring Boot jar.
# The value never touches a file in the repository and is never echoed.
param(
    [string]$PasswordFile = 'C:\Users\Administrator\.tinker\db_password.txt'
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $PasswordFile)) {
    Write-Error "[coc-ops] password file not found: $PasswordFile"
    exit 2
}
$pw = [System.IO.File]::ReadAllText($PasswordFile).Trim()
if ($pw.Length -ge 2) {
    if (($pw.StartsWith('"') -and $pw.EndsWith('"')) -or ($pw.StartsWith("'") -and $pw.EndsWith("'"))) {
        $pw = $pw.Substring(1, $pw.Length - 2)
    }
}
if ([string]::IsNullOrEmpty($pw)) { exit 3 }
[Console]::Out.Write($pw)
