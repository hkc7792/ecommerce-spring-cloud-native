---
name: push-changes-to-remote
description: Builds project, logically groups added/modified files into atomic commits, and pushes to current remote branch.
---

Perform the following workflow step-by-step:

1. **Check Status**
   - Run `git status` to identify modified, untracked, and deleted files.
   - If clean, notify the user and exit.

2. **Build Verification**
   - Detect project build tool (e.g., `./mvnw compile`, `./gradlew build`, or `npm run build`).
   - Run the build command.
   - **Stop execution immediately if the build fails.**

3. **Atomic Commits**
   - Group modified and untracked files into logical, atomic units (e.g., config, domain/service layer, tests).
   - Stage each group separately with `git add <files>`.
   - Commit each group using Conventional Commit messages (e.g., `feat:`, `fix:`, `chore:`).

4. **Push to Remote**
   - Get active branch name via `git rev-parse --abbrev-ref HEAD`.
   - Push all commits at once: `git push origin <branch-name>`.
   - Output summary of commits made and push status.