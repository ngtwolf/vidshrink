# Publish VidShrink to GitHub

This repository is prepared for the private GitHub repository `ngtwolf/vidshrink`.

1. In GitHub, create an empty private repository named `vidshrink` under `ngtwolf`.
2. Do not add a README, `.gitignore`, license, or other starter file in GitHub.
3. Run this command from the repository root in a normal local PowerShell window:

```powershell
.\scripts\publish-to-github.ps1 -Push
```

The script removes the starter repository's tracked `.idea/.gitignore` entry without deleting the local file. It stages the intended source files, checks that local Android Studio state and debug APKs are excluded, creates a commit, renames the default branch to `main`, adds `https://github.com/ngtwolf/vidshrink.git` as `origin`, and pushes the branch.

Debug APKs in `releases` stay on the computer. Use GitHub Releases for user-facing APKs.
