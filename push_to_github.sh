#!/usr/bin/env bash
# ==============================================================================
# Git Automation Script for Renewable Wind Turbine Fleet Predictive Maintenance Engine
# Target User: VikasiniE03 | Target Repo: WindTurbineFleetEngine
# ==============================================================================

set -e

REPO_NAME="WindTurbineFleetEngine"
TARGET_USER="VikasiniE03"
REMOTE_URL="https://github.com/${TARGET_USER}/${REPO_NAME}.git"

echo "=============================================================================="
echo "          PUSH TO GITHUB: RENEWABLE WIND TURBINE FLEET ENGINE                 "
echo "=============================================================================="
echo "Target User       : ${TARGET_USER}"
echo "Target Repository : ${REPO_NAME}"
echo "Remote URL        : ${REMOTE_URL}"
echo "------------------------------------------------------------------------------"

# 1. Initialize git if not already a repository
if [ ! -d ".git" ]; then
    echo "[1/5] Initializing Git repository..."
    git init
    git branch -M main
else
    echo "[1/5] Existing Git repository detected."
    git branch -M main
fi

# 2. Configure Git user credentials
git config user.name "${TARGET_USER}"
git config user.email "vikasini.eswaran.03.06@gmail.com"

# 3. Add all files
echo "[2/5] Staging project files..."
git add -A

# 4. Commit changes
echo "[3/5] Committing changes..."
COMMIT_MSG="feat: complete Renewable Wind Turbine Fleet Predictive Maintenance Engine (Project Set 24)"
git commit -m "${COMMIT_MSG}" || echo "Nothing new to commit."

# 5. Connect Remote
echo "[4/5] Setting up remote repository connection..."
if git remote | grep -q "origin"; then
    git remote set-url origin "${REMOTE_URL}"
else
    git remote add origin "${REMOTE_URL}"
fi

# 6. Push to GitHub
echo "[5/5] Pushing to GitHub (main branch)..."
git push -u origin main

echo "=============================================================================="
echo "[SUCCESS] Project successfully deployed and pushed to:"
echo "https://github.com/${TARGET_USER}/${REPO_NAME}"
echo "=============================================================================="
