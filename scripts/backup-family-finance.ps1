param(
    [string]$Target = ""
)

$ErrorActionPreference = "Stop"
$source = Split-Path -Parent $PSScriptRoot
$desktop = [Environment]::GetFolderPath("Desktop")
if ([string]::IsNullOrWhiteSpace($Target)) {
    $Target = Join-Path $desktop "FamilyFinance-Backup-Latest"
}

if (-not (Test-Path -LiteralPath $source)) { throw "Source project not found: $source" }
if (-not (Test-Path -LiteralPath $desktop)) { throw "Desktop folder not found: $desktop" }

New-Item -ItemType Directory -Path $Target -Force | Out-Null
$sourceTarget = Join-Path $Target "source"
$apkTarget = Join-Path $Target "APKs"
New-Item -ItemType Directory -Path $sourceTarget -Force | Out-Null
New-Item -ItemType Directory -Path $apkTarget -Force | Out-Null

# Keep the complete working tree, including generated folders, but omit Git's internal files.
robocopy $source $sourceTarget /E /XD .git /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null

$activity = Join-Path $source "..\logs\activity.csv"
if (Test-Path -LiteralPath $activity) {
    Copy-Item -LiteralPath $activity -Destination $Target -Force
}

$downloads = Join-Path ([Environment]::GetFolderPath("UserProfile")) "Downloads"
Get-ChildItem -LiteralPath $downloads -Filter "FamilyFinance-*.apk" -File -ErrorAction SilentlyContinue |
    Copy-Item -Destination $apkTarget -Force

$bundle = Join-Path $Target "family-finance-app-history.bundle"
git -C $source bundle create $bundle --all | Out-Null

$commit = (git -C $source rev-parse HEAD).Trim()
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
@"
Family Finance automatic backup
===============================
Updated: $now
Commit: $commit

This folder is refreshed automatically after local commits.
It contains the complete source tree, generated build/.gradle/node_modules folders when present,
APK files from Downloads, the activity log, and a Git history bundle.

Firebase production data is remote and must be exported separately from Firebase when required.
"@ | Set-Content -LiteralPath (Join-Path $Target "BACKUP-README.txt") -Encoding UTF8

Write-Output "Family Finance backup updated: $Target"
