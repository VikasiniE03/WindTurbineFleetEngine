# ==============================================================================
# PowerShell GitHub Automation Script
# Project Assignment Set 24: Renewable Wind Turbine Fleet Engine
# Target User: VikasiniE03
# ==============================================================================

$TargetUser = "VikasiniE03"
$RepoName   = "WindTurbineFleetEngine"
$RemoteUrl  = "https://github.com/$TargetUser/$RepoName.git"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "          PUSH TO GITHUB: RENEWABLE WIND TURBINE FLEET ENGINE                 " -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "Target User       : $TargetUser"
Write-Host "Target Repository : $RepoName"
Write-Host "Remote URL        : $RemoteUrl"
Write-Host "------------------------------------------------------------------------------"

# 1. Retrieve GitHub OAuth token via git credential helper
$credInput = "protocol=https`nhost=github.com`n"
$credOutput = $credInput | git credential fill
$token = ""
foreach ($line in ($credOutput -split "`n")) {
    if ($line -match "^password=(.+)$") {
        $token = $matches[1].Trim()
    }
}

if ($token) {
    Write-Host "[1/6] GitHub OAuth token detected via Git Credential Manager." -ForegroundColor Green
    # Check if remote repository exists on GitHub
    $headers = @{
        "Authorization" = "Bearer $token"
        "User-Agent"    = "PowerShell-GitHub-Deployer"
        "Accept"        = "application/vnd.github.v3+json"
    }

    try {
        $checkUri = "https://api.github.com/repos/$TargetUser/$RepoName"
        $repoInfo = Invoke-RestMethod -Uri $checkUri -Headers $headers -Method Get -ErrorAction Stop
        Write-Host "[2/6] Remote repository already exists on GitHub ($($repoInfo.html_url))." -ForegroundColor Green
    } catch {
        Write-Host "[2/6] Repository does not exist yet. Creating on GitHub via REST API..." -ForegroundColor Yellow
        $createBody = @{
            name        = $RepoName
            description = "Renewable Wind Turbine Fleet Predictive Maintenance Engine (Project Assignment Set 24) - Java 17, Multi-threading, SCADA Ingestion, MySQL 8.0, 2-Tier Architecture"
            private     = $false
            has_issues  = $true
            has_wiki    = $true
        } | ConvertTo-Json

        try {
            $newRepo = Invoke-RestMethod -Uri "https://api.github.com/user/repos" -Headers $headers -Method Post -Body $createBody -ContentType "application/json"
            Write-Host "[SUCCESS] Repository created successfully: $($newRepo.html_url)" -ForegroundColor Green
        } catch {
            Write-Host "[WARN] Could not automatically create repo via API: $_" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "[1/6] No cached token found; proceeding with standard Git credential manager." -ForegroundColor Yellow
}

# 2. Initialize Git
if (-not (Test-Path ".git")) {
    Write-Host "[3/6] Initializing local Git repository..." -ForegroundColor Yellow
    git init
    git branch -M main
} else {
    Write-Host "[3/6] Existing local Git repository found." -ForegroundColor Yellow
    git branch -M main
}

# 3. Configure identity
git config user.name "$TargetUser"
git config user.email "vikasini.eswaran.03.06@gmail.com"

# 4. Stage and commit
Write-Host "[4/6] Staging files..." -ForegroundColor Yellow
git add -A

Write-Host "[5/6] Committing changes..." -ForegroundColor Yellow
git commit -m "feat: complete Renewable Wind Turbine Fleet Predictive Maintenance Engine (Project Assignment Set 24)"

# 5. Remote configuration
$remotes = git remote
if ($remotes -contains "origin") {
    git remote set-url origin $RemoteUrl
} else {
    git remote add origin $RemoteUrl
}

# 6. Push
Write-Host "[6/6] Pushing to GitHub (main branch)..." -ForegroundColor Yellow
git push -u origin main

Write-Host "==============================================================================" -ForegroundColor Green
Write-Host "[SUCCESS] Codebase pushed successfully to GitHub!" -ForegroundColor Green
Write-Host "Repository URL: $RemoteUrl" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Green
