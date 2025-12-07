# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.1] - 2025-12-07

### Changed

- Rate limiting no longer enforces cooldown between every request
- Cooldown now only applies as a 10-second penalty after exceeding the rate limit
- Renamed config key `cooldown-ms` to `penalty-cooldown-ms`

## [0.6.0] - 2025-12-07

### Added

- Environment variable support for secrets (`AMS_DISCORD_TOKEN`, `AMS_GUILD_ID`)
- Command rate limiting with configurable cooldown and burst protection
- Input validation for Minecraft usernames
- Audit logging to console and `audit.log` file for admin actions
- MIT LICENSE file

### Security

- Rate limiting prevents command spam (3-second cooldown, 60 requests/minute)
- Console commands exempt from rate limiting for trusted operators

## [0.5.0] - 2025-12-07

### Changed

- Rebranded from "AMS Discord" to "AMSSync"
- Renamed command from `/amslink` to `/amssync`
- Updated package structure from `io.github.darinc.amsdiscord` to `io.github.darinc.amssync`
- Plugin JAR now outputs as `ams-sync-*.jar`
- Permission nodes renamed from `amsdiscord.*` to `amssync.*`

## [0.4.0] - 2025-12-07

### Added

- Configuration validation with detailed error messages before Discord connection
- Error metrics tracking system with `/amssync metrics` command
- Circuit breaker state context in Discord API error logs

### Changed

- Improved thread safety in linking session management
- Enhanced Discord command error handling with null-safe queue callbacks

## [0.3.1] - 2025-12-07

### Fixed

- Player count now correctly decreases when players disconnect

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
