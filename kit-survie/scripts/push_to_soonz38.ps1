$ErrorActionPreference = "Stop"
$RepoUrl = "https://github.com/Soonz-38/kit-survie.git"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "==> Cible GitHub : $RepoUrl"

# 1. Si GITHUB_TOKEN ou GH_TOKEN est défini, utiliser le publieur API direct
if ($env:GITHUB_TOKEN -or $env:GH_TOKEN) {
    Write-Host "==> Token GitHub détecté, publication directe via l'API GitHub..."
    node "$RootDir\scripts\publish_to_github.mjs" "Soonz-38/kit-survie"
    exit $LASTEXITCODE
}

# 2. Rechercher git.exe dans les emplacements standards Windows / GitHub Desktop
function Find-GitExe {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $candidates = @(
        "C:\Program Files\Git\cmd\git.exe",
        "C:\Program Files (x86)\Git\cmd\git.exe",
        "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }

    $ghDesktop = Get-ChildItem -Path "$env:LOCALAPPDATA\GitHubDesktop" -Filter "git.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($ghDesktop) { return $ghDesktop.FullName }

    return $null
}

$gitExe = Find-GitExe
if (-not $gitExe) {
    Write-Host "==> Git n'est pas encore installé. Installation automatique via winget (Git.Git)..."
    winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements
    $gitExe = Find-GitExe
}

if (-not $gitExe) {
    Write-Error "Impossible de trouver git.exe après installation."
    exit 1
}

Write-Host "==> Utilisation de Git : $gitExe"

if (-not (Test-Path "$RootDir\.git")) {
    & $gitExe init -b main
} else {
    & $gitExe branch -M main
}

# Configurer un auteur local si aucun user.name/user.email global n'existe
$userName = & $gitExe config user.name
if (-not $userName) {
    & $gitExe config user.name "Soonz-38"
    & $gitExe config user.email "soonz-38@users.noreply.github.com"
}

$remotes = & $gitExe remote
if ($remotes -contains "origin") {
    & $gitExe remote set-url origin $RepoUrl
} else {
    & $gitExe remote add origin $RepoUrl
}

& $gitExe add -A
$status = & $gitExe status --porcelain
if ($status) {
    & $gitExe commit -m "feat: assemble Kiwix, MedGemma-FA, Survival-Station & Organic Maps into unified offline MCP server for Android Gemma 4 E4B IT"
}

Write-Host "==> Envoi (git push) vers $RepoUrl ..."
& $gitExe push -u origin main --force
Write-Host "==> Déploiement terminé avec succès sur https://github.com/Soonz-38/kit-survie !"

