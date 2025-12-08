# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2025-12-07

### Added

- Dynamic player count display in Discord bot status (e.g., "Playing 12 players online")
- Optional bot nickname updates with player count
- Event-driven updates with debouncing and rate limiting
- Configurable activity type, message templates, and update intervals

## [0.2.0] - 2025-12-07

### Added

- Username parameter to `/mcstats` command for looking up other players
- Support for Discord mentions, Discord IDs, and Minecraft usernames as lookup targets

### Changed

- Updated command description to reflect new functionality
- Replaced deprecated JDA `asTag` with `name` for user display

## [0.1.0] - 2025-12-07

### Added

- CLAUDE.md documentation for Claude Code integration
- Architecture overview and critical implementation details
- Build commands and development workflow guidance
