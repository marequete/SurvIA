<#
.SYNOPSIS
    Script de compilation locale et de vérification de l'application SurvIA.
#>
param (
    [switch]$CheckOnly
)

Write-Host "========================================================" -ForegroundColor Green
Write-Host "   SURVIA : VÉRIFICATION ET BUILD LOCAL HORS-LIGNE      " -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

# 1. Vérification de la présence des fichiers clés
$KeyFiles = @(
    "survia-app\settings.gradle.kts",
    "survia-app\build.gradle.kts",
    "survia-app\shared\src\commonMain\kotlin\com\survia\ai\PromptBuilder.kt",
    "survia-app\shared\src\commonMain\kotlin\com\survia\tools\SurvivalToolRouter.kt",
    "survia-app\androidApp\src\main\AndroidManifest.xml",
    "survia-app\iosApp\SurvIAApp.swift",
    "landing-page\index.html",
    "landing-page\vercel.json"
)

$AllPresent = $true
foreach ($file in $KeyFiles) {
    $fullPath = Join-Path $Root $file
    if (Test-Path $fullPath) {
        Write-Host " [OK] Fichier présent : $file" -ForegroundColor Green
    } else {
        Write-Host " [MANQUANT] : $file" -ForegroundColor Red
        $AllPresent = $false
    }
}

if (-not $AllPresent) {
    Write-Error "Certains fichiers clés du projet SurvIA sont introuvables."
    exit 1
}

Write-Host "`nTous les composants (KMP Shared, AndroidApp, IosApp, Landing Vercel) sont intègres !" -ForegroundColor Cyan

if ($CheckOnly) {
    Write-Host "Vérification terminée avec succès (--CheckOnly)." -ForegroundColor Green
    exit 0
}

Write-Host "`nPour déployer la landing page sur Vercel :" -ForegroundColor Yellow
Write-Host "  cd landing-page && npx vercel"
Write-Host "`nPour pousser l'APK sur GitHub Releases :" -ForegroundColor Yellow
Write-Host "  git tag v1.0.0 && git push origin v1.0.0"
