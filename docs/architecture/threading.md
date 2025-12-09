# Threading & Concurrency

**Complexity**: Advanced
**Key Files**:
- [`AMSSyncPlugin.kt`](../../src/main/kotlin/io/github/darinc/amssync/AMSSyncPlugin.kt)
- [`discord/PlayerCountPresence.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/PlayerCountPresence.kt)
- [`discord/CircuitBreaker.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/CircuitBreaker.kt)

## The Challenge

Minecraft plugins operate in a multi-threaded environment:

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Plugin Environment                         │
├─────────────────────────────────────────────────────────────────────┤
│  Bukkit Main Thread        │  JDA Thread Pool        │  Schedulers  │
│  - Bukkit API calls        │  - Discord events       │  - Timeouts  │
│  - Event handlers          │  - Slash commands       │  - Presence  │
│  - Player data             │  - Message sending      │  - Cleanup   │
└─────────────────────────────────────────────────────────────────────┘
```

**The rules**:
1. Bukkit API must be called from the main thread
2. Discord operations run on JDA's thread pool
3. Long operations must not block the main thread
4. Shared state must be thread-safe

## Thread Safety Patterns

### Pattern 1: Atomic Types

For simple counters and flags, use `java.util.concurrent.atomic`:

```kotlin
// In CircuitBreaker.kt
private val state = AtomicReference(State.CLOSED)
private val failureCount = AtomicInteger(0)
private val successCount = AtomicInteger(0)
private val lastFailureTime = AtomicLong(0L)
```

**Why atomics?**
- Lock-free operations (no blocking)
- Thread-safe without `synchronized`
- Better performance under contention

**Common atomic operations**:
```kotlin
// Atomic get and set
val current = state.get()
state.set(State.OPEN)

// Compare and set (conditional update)
if (state.compareAndSet(State.CLOSED, State.OPEN)) {
    // Only one thread wins this race
    logger.info("State changed!")
}

// Atomic increment
val newCount = failureCount.incrementAndGet()
```

### Pattern 2: ConcurrentHashMap

For caches and lookup tables:

```kotlin
// In McmmoApiWrapper.kt
private val leaderboardCache = ConcurrentHashMap<String, CachedLeaderboard>()

// In RateLimiter.kt
private val requestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
private val penaltyExpiry = ConcurrentHashMap<String, Long>()

// In ErrorMetrics.kt
private val commandCounts = ConcurrentHashMap<String, AtomicLong>()
```

**Safe operations**:
```kotlin
// Atomic compute if absent
val entry = cache.computeIfAbsent(key) { createNewEntry() }

// Atomic put if absent
cache.putIfAbsent(key, value)

// Thread-safe iteration (snapshot)
cache.entries.forEach { (k, v) -> process(k, v) }
```

**Unsafe patterns to avoid**:
```kotlin
// BAD: Check-then-act race condition
if (!cache.containsKey(key)) {
    cache[key] = computeValue()  // Another thread might add between check and put!
}

// GOOD: Atomic operation
cache.computeIfAbsent(key) { computeValue() }
```

### Pattern 3: Synchronized Collections

For ordered/bounded caches:

```kotlin
// In AvatarFetcher.kt - LRU cache with max size
private val avatarCache = Collections.synchronizedMap(
    object : LinkedHashMap<String, CachedAvatar>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedAvatar>): Boolean {
            return size > maxSize
        }
    }
)
```

### Pattern 4: Main Thread Only

Some services intentionally avoid thread safety:

```kotlin
// In UserMappingService.kt
/**
 * NOT thread-safe. Only accessed from Bukkit main thread.
 */
class UserMappingService(private val plugin: AMSSyncPlugin) {
    private val discordToMinecraft = mutableMapOf<String, String>()
    private val minecraftToDiscord = mutableMapOf<String, String>()
}
```

**Why?** Thread safety adds overhead. If you can guarantee single-thread access, skip it.

## Thread Pools in AMSSync

### JDA Thread Pool

JDA manages its own thread pool for Discord operations:

```kotlin
// Discord events fire on JDA threads
override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
    // This runs on JDA thread, NOT Bukkit main thread!

    // BAD: Direct Bukkit API call
    val player = Bukkit.getPlayer("Steve")  // May crash!

    // GOOD: Schedule on main thread
    Bukkit.getScheduler().runTask(plugin) {
        val player = Bukkit.getPlayer("Steve")  // Safe
    }
}
```

### Custom Executor Services

For background tasks with controlled threading:

```kotlin
// In PlayerCountPresence.kt
private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "AMSSync-Presence").apply { isDaemon = true }
}
```

**Key settings**:
- `newSingleThreadScheduledExecutor`: One thread, queued tasks
- `isDaemon = true`: Thread dies with JVM (won't prevent shutdown)
- Named thread: Easier debugging (`AMSSync-Presence` in stack traces)

```kotlin
// In TimeoutManager.kt
private val executor = Executors.newScheduledThreadPool(2) { runnable ->
    Thread(runnable, "AMSSync-Timeout").apply { isDaemon = true }
}
```

**Two threads**: Allows timeout scheduling while one task is running.

## Crossing Thread Boundaries

### From JDA to Bukkit Main Thread

```kotlin
// In ChatBridge.kt
override fun onMessageReceived(event: MessageReceivedEvent) {
    // Running on JDA thread

    val message = event.message.contentDisplay

    // Schedule on Bukkit main thread
    Bukkit.getScheduler().runTask(plugin, Runnable {
        // Now on main thread - safe to call Bukkit API
        Bukkit.broadcastMessage(message)
    })
}
```

### From Bukkit to Async

```kotlin
// In SlashCommandListener.kt
// Already on JDA thread, but for Bukkit events:
Bukkit.getScheduler().runTaskAsynchronously(plugin) {
    // Runs on async thread - don't call Bukkit API here!
    val result = expensiveComputation()

    // Return to main thread with result
    Bukkit.getScheduler().runTask(plugin) {
        applyResult(result)
    }
}
```

### CompletableFuture for Async Operations

```kotlin
// In McMMOEventListener.kt
CompletableFuture.supplyAsync {
    // Runs on ForkJoinPool.commonPool()
    val headImage = avatarFetcher.fetchHeadAvatar(playerName, uuid, provider, size)
    cardRenderer.renderSkillMilestoneCard(playerName, skill, level, headImage)
}.thenAccept { cardImage ->
    // Still on async thread
    sendImageToDiscord(cardImage, filename, description)
}.exceptionally { e ->
    // Handle errors
    plugin.logger.warning("Failed: ${e.message}")
    sendFallbackEmbed()
    null
}
```

## Debouncing Pattern

Batch rapid events to reduce API calls:

```kotlin
// In PlayerCountPresence.kt
private val lastUpdateTime = AtomicLong(0L)
private val pendingUpdate = AtomicReference<ScheduledFuture<*>?>(null)

private fun scheduleUpdate(adjustment: Int) {
    val currentCount = Bukkit.getOnlinePlayers().size + adjustment

    // Skip if count unchanged
    if (currentCount == lastPlayerCount.get()) return

    // Cancel any pending update
    pendingUpdate.get()?.cancel(false)

    // Schedule new update after debounce delay
    val future = executor.schedule(
        { updatePresenceNow() },
        config.debounceSeconds,
        TimeUnit.SECONDS
    )
    pendingUpdate.set(future)
}
```

**Flow**:
```
Player joins  →  Schedule update in 5s
Player joins  →  Cancel previous, schedule new update in 5s
Player joins  →  Cancel previous, schedule new update in 5s
5 seconds pass →  Single update with final count
```

## Common Pitfalls

### 1. Check-Then-Act Race

```kotlin
// BAD: Race condition between check and update
if (circuitBreaker.getState() == State.CLOSED) {
    // Another thread might open circuit here!
    circuitBreaker.execute { ... }
}

// GOOD: Single atomic operation
val result = circuitBreaker.execute { ... }
when (result) {
    is Rejected -> // Handle rejection
    // ...
}
```

### 2. Bukkit API from Wrong Thread

```kotlin
// BAD: Called from JDA thread
override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
    val player = Bukkit.getPlayer(username)  // CRASH!
}

// GOOD: Read data through thread-safe cache, or schedule task
override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
    // Option 1: Use MCMMO's thread-safe methods
    val profile = mcMMO.getDatabaseManager().loadPlayerProfile(uuid)

    // Option 2: Schedule on main thread if needed
    Bukkit.getScheduler().runTask(plugin) {
        val player = Bukkit.getPlayer(username)
    }
}
```

### 3. Forgetting Volatile for Simple Flags

```kotlin
// BAD: May never see update from other thread
private var shutdown = false

// GOOD: Atomic or volatile
private val shutdown = AtomicBoolean(false)
// or
@Volatile private var shutdown = false
```

### 4. Blocking the Main Thread

```kotlin
// BAD: Blocks main thread during network call
@EventHandler
fun onPlayerJoin(event: PlayerJoinEvent) {
    val avatar = avatarFetcher.fetchAvatar(event.player.name)  // Network I/O!
}

// GOOD: Async fetch
@EventHandler
fun onPlayerJoin(event: PlayerJoinEvent) {
    CompletableFuture.supplyAsync {
        avatarFetcher.fetchAvatar(event.player.name)
    }.thenAccept { avatar ->
        // Use avatar (still async, be careful with Bukkit calls)
    }
}
```

## Thread Safety Summary

| Component | Thread Safety | Mechanism | Notes |
|-----------|---------------|-----------|-------|
| `CircuitBreaker` | Thread-safe | Atomics | All state is atomic |
| `RetryManager` | Thread-safe | No shared state | Each call is independent |
| `RateLimiter` | Thread-safe | ConcurrentHashMap | Per-user tracking |
| `ErrorMetrics` | Thread-safe | ConcurrentHashMap + Atomics | All counters atomic |
| `AvatarFetcher` | Thread-safe | Synchronized LinkedHashMap | LRU cache |
| `McmmoApiWrapper` | Thread-safe | ConcurrentHashMap | Leaderboard cache |
| `UserMappingService` | NOT thread-safe | None | Main thread only |
| `DiscordManager` | Thread-safe | JDA handles it | JDA is thread-safe |

## Testing Concurrent Code

```kotlin
class CircuitBreakerConcurrencyTest : DescribeSpec({
    describe("concurrent access") {
        it("handles concurrent failures") {
            val breaker = CircuitBreaker(failureThreshold = 10, ...)

            // Launch many coroutines
            val jobs = (1..100).map {
                async {
                    breaker.execute("test") { throw Exception("fail") }
                }
            }

            jobs.awaitAll()

            // Should have opened circuit
            breaker.getState() shouldBe State.OPEN
            // Failure count should be exactly at or near threshold
            // (some failures may occur after open)
        }
    }
})
```

## Related Documentation

- [Architecture Overview](overview.md) - Component relationships
- [Circuit Breaker](../patterns/circuit-breaker.md) - Thread-safe state machine
- [Rate Limiting](../patterns/rate-limiting.md) - Per-user concurrent tracking
