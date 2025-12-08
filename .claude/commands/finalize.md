Execute the complete version bump and commit workflow after completing any implementation task.

**CRITICAL REQUIREMENTS:**

- Write SHORT TO MEDIUM changelog entries (2-5 bullet points MAXIMUM)
- Do NOT mention stage, phase, or part numbers
- Do NOT include detailed coverage statistics or test counts
- Do NOT sign commits as Claude Code
- Do NOT add Co-Authored-By lines
- Do NOT include emoji or "Generated with Claude Code" attribution

**Steps to execute:**

1. **Determine Version Bump**
   - Read the current version from `build.gradle.kts` (line starts with `version = `)
   - Analyze the changes just implemented
   - Determine the appropriate semantic version bump:
     - MAJOR (x.0.0): Breaking changes, incompatible API changes
     - MINOR (0.x.0): New features, backwards-compatible functionality
     - PATCH (0.0.x): Bug fixes, backwards-compatible fixes
   - For pre-1.0.0 versions: MINOR can include breaking changes, PATCH for fixes
   - Calculate and update the new version number

2. **Update Version in build.gradle.kts**
   - Update the version line: `version = "X.Y.Z"`
   - Remove `-SNAPSHOT` suffix if present (releases should not have SNAPSHOT)

3. **Write Changelog Entry**
   - If CHANGELOG.md doesn't exist, create it with keepachangelog header
   - Add a SHORT TO MEDIUM entry (2-5 bullet points MAX)
   - Use keepachangelog categories: Added, Changed, Fixed, Removed, Deprecated, Security
   - Keep descriptions concise and high-level
   - Format: `## [X.Y.Z] - YYYY-MM-DD` (use calculated version and current date)
   - Insert new entry at the top, after the header

4. **Update Tracking Files**
   - Check for tracking files: TODO.md, TEST.md, REFACTOR.md, TASKS.md, PLAN.md
   - If found, check off completed sections using [x]
   - Update any progress indicators
   - If no tracking file was used, skip this step

5. **Git Commit**
   - Stage all changed files: `git add -A`
   - Create commit using clean format:
     ```
     Release version X.Y.Z

     Category:
     - Change description
     - Change description
     ```
   - Keep commit message simple, clear, and in present tense
   - NO emoji, NO "Generated with Claude Code", NO Co-Authored-By

Execute all steps now.
