# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.13.1] - 2025-12-09

### Added

- Complete documentation coverage with 9 new guides for initialization flow, timeout protection, rate limiting, JDA integration, webhooks, presence status, milestone announcements, audit logging, and contributing guidelines
- All broken links in docs/README.md are now resolved

## [0.13.0] - 2025-12-09

### Added

- Top Skill panel on stats card showing player's highest skill with badge icon
- Example card images in documentation (stats cards and leaderboards)
- CardGeneratorTest for generating documentation images

## [0.12.0] - 2025-12-08

### Added

- Comprehensive documentation in `/docs` directory covering architecture, patterns, and features
- Documentation for circuit breaker, retry-backoff, sealed results, and threading patterns
- Feature guides for Discord commands, chat bridge, image cards, and MCMMO API integration
- Development documentation including build instructions and testing patterns

## [0.11.3] - 2025-12-08

### Fixed

- Fix chat-bridge player heads not displaying when using crafatar provider (UUID format mismatch)
- Align mc-heads URL format in chat-bridge with AvatarFetcher for consistency

## [0.11.2] - 2025-12-08

### Changed

- Update CLAUDE.md with comprehensive documentation of new features and services
- Document image card system, chat bridge, event announcements, and presence features
- Add environment variable configuration and test command documentation

## [0.11.1] - 2025-12-08

### Fixed

- Fix O(n²) performance issue in ErrorMetrics duration tracking by using ArrayDeque
- Replace printStackTrace with logger in Discord connection error handling

### Changed

- Expand detekt configuration with project-appropriate thresholds
- Add thread-safety documentation to UserMappingService
- Replace wildcard imports with explicit imports

## [0.11.0] - 2025-12-08

### Added

- Visual image cards for MCMMO milestone announcements with skill badges and player avatars
- Webhook support for milestone announcements with custom bot name
- Skill tier progression colors (Beginner to Legendary) and power tier badges (Novice to Mythic)

## [0.10.0] - 2025-12-08

### Added

- Visual `/amsstats` command generates Pokemon-style player stat cards with full body skin render
- Visual `/amstop` command generates podium-style leaderboards with top 3 on podium
- Avatar caching system with configurable TTL and provider selection
- Rarity border tiers based on power level (Bronze/Silver/Gold/Diamond)

## [0.9.0] - 2025-12-08

### Added

- Webhook support for chat bridge with player head avatars and usernames
- Configurable avatar provider for chat bridge (mc-heads or crafatar)

## [0.8.0] - 2025-12-07

### Added

- Server start/stop announcements to Discord
- Player death announcements with Minecraft avatar support
- Achievement announcements with advancement type color coding
- Optional Discord webhook support for custom usernames and player avatars
- Configurable avatar providers (mc-heads.net or crafatar.com)

## [0.7.1] - 2025-12-07

### Fixed

- Strip Unicode Variation Selectors from Discord messages to prevent box characters in Minecraft

## [0.7.0] - 2025-12-07

### Added

- Voice channel status display showing online player count with configurable update interval
- MCMMO milestone announcements to Discord for skill and power level achievements
- Two-way chat bridge between Minecraft and Discord with configurable formats
- Configurable notification suppression for chat bridge messages

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
