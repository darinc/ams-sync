package io.github.darinc.amssync.commands.handlers

import io.github.darinc.amssync.commands.CommandContext
import io.github.darinc.amssync.commands.SubcommandHandler
import io.github.darinc.amssync.discord.CircuitBreaker

/**
 * Handles the /amssync metrics command.
 */
class MetricsHandler : SubcommandHandler {
    override val name = "metrics"
    override val usage = "/amssync metrics"

    override fun execute(context: CommandContext, args: List<String>) {
        val (sender, plugin) = context
        val snapshot = plugin.services.errorMetrics.getSnapshot()

        sender.sendMessage("§6§l=== AMSSync Metrics ===")
        sender.sendMessage("§7Uptime: §f${snapshot.uptimeFormatted}")
        sender.sendMessage("")

        // Discord API stats
        sender.sendMessage("§e§lDiscord API:")
        sender.sendMessage("  §aSuccess: §f${snapshot.discordApiStats.successCount}")
        sender.sendMessage("  §cFailures: §f${snapshot.discordApiStats.failureCount}")
        sender.sendMessage("  §6Rejected: §f${snapshot.discordApiStats.rejectedCount}")
        snapshot.discordApiStats.successRate?.let {
            val color = when {
                it >= 0.99 -> "§a"
                it >= 0.95 -> "§e"
                else -> "§c"
            }
            sender.sendMessage("  §7Success Rate: $color${String.format("%.1f", it * 100)}%")
        }

        // Circuit breaker stats
        sender.sendMessage("")
        sender.sendMessage("§e§lCircuit Breaker:")
        sender.sendMessage("  §cTrips: §f${snapshot.circuitBreakerStats.tripCount}")
        sender.sendMessage("  §aRecoveries: §f${snapshot.circuitBreakerStats.recoveryCount}")

        // Current circuit state
        plugin.services.discord.apiWrapper?.getCircuitState()?.let { state ->
            val stateColor = when (state) {
                CircuitBreaker.State.CLOSED -> "§a"
                CircuitBreaker.State.OPEN -> "§c"
                CircuitBreaker.State.HALF_OPEN -> "§e"
            }
            sender.sendMessage("  §7Current State: $stateColor$state")
        }

        // Connection stats
        sender.sendMessage("")
        sender.sendMessage("§e§lConnections:")
        sender.sendMessage("  §7Attempts: §f${snapshot.connectionStats.attemptCount}")
        sender.sendMessage("  §aSuccess: §f${snapshot.connectionStats.successCount}")
        sender.sendMessage("  §cFailures: §f${snapshot.connectionStats.failureCount}")

        // Command stats (if any)
        if (snapshot.commandStats.isNotEmpty()) {
            sender.sendMessage("")
            sender.sendMessage("§e§lCommands:")
            snapshot.commandStats.forEach { (cmd, stats) ->
                val rate = stats.successRate?.let { String.format("%.0f%%", it * 100) } ?: "N/A"
                val latency = stats.avgLatencyMs?.let { String.format("%.0fms", it) } ?: "N/A"
                sender.sendMessage("  §f$cmd§7: ${stats.successCount}/${stats.successCount + stats.failureCount} ($rate), avg: $latency")
            }
        }

        // Error types (if any)
        if (snapshot.errorStats.isNotEmpty()) {
            sender.sendMessage("")
            sender.sendMessage("§e§lErrors by Type:")
            snapshot.errorStats.forEach { (type, count) ->
                sender.sendMessage("  §c$type§7: $count")
            }
        }

        // Compression stats
        sender.sendMessage("")
        sender.sendMessage("§e§lCompression:")
        val compression = snapshot.compressionStats
        val totalRuns = compression.successCount + compression.failureCount
        if (totalRuns > 0) {
            sender.sendMessage("  §aRuns: §f${compression.successCount} §7(${compression.scheduledCount} scheduled, ${compression.catchupCount} catch-up)")
            if (compression.failureCount > 0) {
                sender.sendMessage("  §cFailures: §f${compression.failureCount}")
            }
            compression.avgTotalDurationMs?.let {
                sender.sendMessage("  §7Avg Duration: §f${String.format("%.0f", it)}ms")
            }
            compression.p95TotalDurationMs?.let {
                sender.sendMessage("  §7P95 Duration: §f${it}ms")
            }
            compression.lastRunStats?.let { last ->
                sender.sendMessage("  §7Last Run: §f${last.totalDurationMs}ms")
                sender.sendMessage("    §7Records: §fhourly+${last.hourlyCreated}, daily+${last.dailyCreated}, weekly+${last.weeklyCreated}")
            }
        } else {
            sender.sendMessage("  §7No compression runs yet")
        }
    }
}
