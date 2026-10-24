param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

function Invoke-Git([string[]]$Arguments) {
    $output = & git -C $RepositoryRoot -c safe.directory=$RepositoryRoot @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
    return @($output)
}

$requiredFiles = @(".gitignore", ".gitattributes", "README.md")
$missingFiles = @($requiredFiles | Where-Object { -not (Test-Path -LiteralPath (Join-Path $RepositoryRoot $_)) })
if ($missingFiles.Count -gt 0) {
    throw "Missing repository files: $($missingFiles -join ', ')"
}

$trackedFiles = Invoke-Git @("ls-files")
$blockedFiles = @($trackedFiles | Where-Object {
    $_ -match '(^|/)\.idea/' -or
    $_ -match '\.iml$' -or
    $_ -eq 'local.properties' -or
    $_ -match '^releases/.*-debug\.apk$'
})
if ($blockedFiles.Count -gt 0) {
    throw "Files that must remain local are tracked: $($blockedFiles -join ', ')"
}

$ignoredPaths = @(
    ".idea",
    "local.properties",
    "releases/VidComp-1.0-debug.apk",
    "releases/VidShrink-1.0-debug.apk"
)
$notIgnored = @()
foreach ($path in $ignoredPaths) {
    $fullPath = Join-Path $RepositoryRoot $path
    if (Test-Path -LiteralPath $fullPath) {
        & git -C $RepositoryRoot -c safe.directory=$RepositoryRoot check-ignore -q -- $path
        if ($LASTEXITCODE -ne 0) {
            $notIgnored += $path
        }
    }
}
if ($notIgnored.Count -gt 0) {
    throw "Expected ignored paths are not ignored: $($notIgnored -join ', ')"
}

[ordered]@{
    result = "PASS"
    tracked_file_count = $trackedFiles.Count
    required_files = $requiredFiles
    ignored_paths_checked = $ignoredPaths
} | ConvertTo-Json
