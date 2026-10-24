param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$RemoteUrl = "https://github.com/ngtwolf/vidshrink.git",
    [switch]$Push
)

$ErrorActionPreference = "Stop"

function Invoke-Git([string[]]$Arguments) {
    & git -C $RepositoryRoot -c safe.directory=$RepositoryRoot @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
}

$remoteNames = @(& git -C $RepositoryRoot -c safe.directory=$RepositoryRoot remote)
if ($LASTEXITCODE -ne 0) {
    throw "Could not list Git remotes."
}

$existingRemote = $null
if ($remoteNames -contains "origin") {
    $existingRemote = & git -C $RepositoryRoot -c safe.directory=$RepositoryRoot remote get-url origin
    if ($LASTEXITCODE -ne 0) {
        throw "Could not read the origin remote URL."
    }
}

if ($null -ne $existingRemote -and $existingRemote -ne $RemoteUrl) {
    throw "origin already points to $existingRemote. Expected $RemoteUrl."
}

# Remove the starter IDE file from Git without deleting the developer's local copy.
Invoke-Git @("rm", "--cached", "--ignore-unmatch", ".idea/.gitignore")
Invoke-Git @("add", "-A")

& (Join-Path $PSScriptRoot "check-github-publish.ps1") -RepositoryRoot $RepositoryRoot
if ($LASTEXITCODE -ne 0) {
    throw "The repository did not pass the publishing check."
}

$staged = & git -C $RepositoryRoot -c safe.directory=$RepositoryRoot diff --cached --quiet
if ($LASTEXITCODE -eq 1) {
    $env:GIT_AUTHOR_NAME = "ngtwolf"
    $env:GIT_AUTHOR_EMAIL = "6609813+ngtwolf@users.noreply.github.com"
    $env:GIT_COMMITTER_NAME = $env:GIT_AUTHOR_NAME
    $env:GIT_COMMITTER_EMAIL = $env:GIT_AUTHOR_EMAIL
    Invoke-Git @("commit", "-m", "Publish VidShrink source")
}
elseif ($LASTEXITCODE -ne 0) {
    throw "Could not inspect staged changes."
}

Invoke-Git @("branch", "-M", "main")
if ($LASTEXITCODE -ne 0) {
    throw "Could not rename the default branch to main."
}

if ($existingRemote -ne $RemoteUrl) {
    Invoke-Git @("remote", "add", "origin", $RemoteUrl)
}

if ($Push) {
    Invoke-Git @("push", "-u", "origin", "main")
}
else {
    Write-Host "The repository is committed and connected to $RemoteUrl. Run this script again with -Push to publish it."
}
