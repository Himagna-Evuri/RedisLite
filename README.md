# RedisLite

**A Lightweight, Redis-Inspired In-Memory Key-Value Store Built with Java**

RedisLite is a Java-based, Redis-inspired key-value store that demonstrates the core concepts behind in-memory databases. It combines fast in-memory data access, TTL-based expiration, background cleanup, file-based persistence, and command-line interaction in a modular implementation.

The project explores how key-value stores manage data, handle expiration, preserve state across restarts, and coordinate concurrent operations.

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Supported Commands](#supported-commands)
- [How TTL Works](#how-ttl-works)
- [Persistence and Recovery](#persistence-and-recovery)
- [Concurrency and Thread Safety](#concurrency-and-thread-safety)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Example Session](#example-session)
- [Testing](#testing)
- [Design Decisions](#design-decisions)
- [Limitations and Future Improvements](#limitations-and-future-improvements)
- [Learning Outcomes](#learning-outcomes)
- [Author](#author)

## Features

- **In-memory storage:** Stores key-value pairs using Java's `ConcurrentHashMap`.
- **Core operations:** Supports setting, retrieving, deleting, and checking keys.
- **TTL-based expiration:** Allows keys to expire after a specified duration.
- **Lazy expiration:** Checks whether a key has expired when it is accessed.
- **Background cleanup:** Periodically scans the store and removes expired entries.
- **Persistent storage:** Saves database state to a local `dump.rdb` file.
- **Restart recovery:** Loads previously saved entries when the application starts.
- **Safer file updates:** Writes to a temporary file before replacing the main database file.
- **Escaped record format:** Supports commas and backslashes in keys and values.
- **Command-line interface:** Accepts Redis-inspired commands through a parser and command handler.
- **Automated testing:** Uses JUnit 5 to test storage and expiration behavior.
- **Concurrency-aware persistence:** Uses synchronization to coordinate persistence operations that share the same lock.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Core implementation |
| Maven | Build and dependency management |
| JUnit 5 | Automated testing |
| `ConcurrentHashMap` | Concurrent in-memory storage |
| Java I/O and NIO | File operations and persistence |
| Git and GitHub | Version control and source-code hosting |
| IntelliJ IDEA | Development environment |

## Architecture

RedisLite uses a layered design that separates command processing, storage, expiration, and persistence responsibilities.

```text
                 User
                   |
                   v
                  Main
                   |
                   v
             CommandParser
                   |
                   v
             CommandHandler
                   |
                   v
              KeyValueStore
                   |
          +--------+---------+
          |        |         |
          v        v         v
      In-Memory   TTL      Persistence
       Storage   Cleanup    Manager
          |        |         |
          v        v         v
    ConcurrentHashMap     dump.rdb
```

### Core Components

**`Main`**

The application entry point. It reads user commands from the command line and passes them to the command-processing layer.

**`CommandParser`**

Processes command-line input, separates command arguments, and normalizes command names.

**`CommandHandler`**

Interprets supported commands and delegates the corresponding operations to `KeyValueStore`.

**`KeyValueStore`**

The central storage component. It manages key-value operations, validates entries against expiration timestamps, coordinates mutations with persistence, and initializes the store.

**`Entry`**

Represents an individual stored record. It contains the value and an optional absolute expiration timestamp.

**`ExpiryCleaner`**

Runs a background cleanup process that periodically identifies expired entries and removes them from memory.

**`PersistenceManager`**

Handles database file creation, loading saved entries, serializing the store, and replacing the database file through a temporary file.

## Supported Commands

RedisLite provides a simple command-line interface inspired by Redis.

| Command | Description | Example |
|---|---|---|
| `SET` | Stores a key-value pair | `SET name Himagna` |
| `SET` with TTL | Stores a key with an expiration time in seconds | `SET session abc 10` |
| `GET` | Retrieves a key's value | `GET name` |
| `DEL` | Deletes a key | `DEL name` |
| `EXISTS` | Checks whether a key exists | `EXISTS name` |
| `TTL` | Returns the remaining lifetime | `TTL session` |
| `KEYS` | Lists active keys | `KEYS` |
| `FLUSHDB` | Clears the current store | `FLUSHDB` |
| `EXIT` | Exits the application | `EXIT` |

These commands provide a Redis-inspired interface; RedisLite is not a fully Redis-compatible server.

### TTL Return Values

The `TTL` operation uses the following return conventions:

- `-1`: The key exists and has no expiration.
- `-2`: The key does not exist or has expired.
- Non-negative value: The remaining lifetime in seconds.

Because the remaining lifetime is expressed in whole seconds, a valid key with less than one second remaining may report `0`.

## How TTL Works

TTL stands for **Time To Live**. It specifies how long a key should remain valid after it is stored.

When a key is created with a TTL, RedisLite calculates an absolute expiration timestamp:

```text
expiryTime = currentTimeMillis + (ttlSeconds * 1000)
```

The expiration timestamp is stored in the `Entry` object alongside the value.

### Expiration Strategies

RedisLite uses two complementary expiration mechanisms.

**1. Lazy expiration**

When an operation accesses a key, the store checks whether the entry has expired. If it has, RedisLite removes the expired entry rather than returning it as a valid value.

**2. Active expiration**

A background `ExpiryCleaner` periodically scans the store and removes expired entries, even if they are never accessed again.

### Expiration Flow

```text
SET session abc 10
        |
        v
Calculate expiration timestamp
        |
        v
Store Entry(value, expiryTime)
        |
        +--------------------------+
        |                          |
        v                          v
 Check on access             Background scan
 (lazy expiration)           (active expiration)
        |                          |
        +------------+-------------+
                     |
                     v
             Remove expired entry
```

Combining these approaches helps prevent expired entries from remaining in memory indefinitely while the application is running.

## Persistence and Recovery

An in-memory store loses its data when the application terminates unless the data is saved elsewhere. RedisLite addresses this using local file-based persistence.

The database file is:

```text
dump.rdb
```

### Saving Data

When supported storage mutations occur, `PersistenceManager` serializes the current database state into the persistence file.

The record format is:

```text
key,value,expiryTime
```

Example:

```text
name,Himagna,null
session,abc,1750000000000
```

Here, `null` represents a key without an expiration timestamp. A numeric expiration timestamp represents a key with a TTL.

Commas and backslashes in keys and values are escaped so that records can be parsed correctly.

### Safer File Replacement

Instead of directly overwriting the main database file, RedisLite writes to a temporary file first:

```text
dump.rdb.tmp
```

The process is:

1. Capture the current store state.
2. Write the serialized records to the temporary file.
3. Replace `dump.rdb` with the temporary file after the write succeeds.
4. Clean up any remaining temporary file on a best-effort basis.

This approach reduces the risk of leaving the main database file partially written if a write operation fails. It does not guarantee recovery from every crash or storage failure.

### Loading Data After Restart

During initialization, RedisLite creates the database file if necessary and loads existing valid records into memory. Expired records are skipped.

This allows previously persisted data to be restored when RedisLite starts again.

## Concurrency and Thread Safety

RedisLite uses `ConcurrentHashMap` for in-memory storage because the application can access the store while a background thread performs expiration cleanup.

The implementation also uses a shared lock to coordinate operations that need consistent interaction between memory and persistence.

Important design choices include:

- **Concurrent storage:** `ConcurrentHashMap` supports concurrent map operations.
- **Coordinated persistence:** Save operations sharing the same lock are serialized.
- **Conditional removal:** Expiration cleanup checks that the entry being removed is still the expected entry, helping avoid deleting a replacement value.
- **Temporary-file replacement:** Reduces the risk of partially overwriting the primary database file.
- **Failure handling:** Storage mutations can roll back in-memory changes when their persistence save fails.

Thread safety is not provided by `ConcurrentHashMap` alone. Multi-step operations require appropriate synchronization, and the current design does not coordinate multiple application processes writing to the same database file.

## Project Structure

```text
RedisLite/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── redislite/
│   │               ├── Main.java
│   │               ├── model/
│   │               │   └── Entry.java
│   │               ├── parser/
│   │               │   ├── CommandParser.java
│   │               │   └── CommandHandler.java
│   │               ├── storage/
│   │               │   └── KeyValueStore.java
│   │               ├── persistence/
│   │               │   └── PersistenceManager.java
│   │               └── ttl/
│   │                   └── ExpiryCleaner.java
│   └── test/
│       └── java/
│           └── com/
│               └── redislite/
│                   ├── KeyValueStoreTest.java
│                   └── ttl/
│                       └── ExpiryCleanerTest.java
├── pom.xml
├── .gitignore
└── README.md
```

## Getting Started

### Prerequisites

- Java JDK 21 or later
- Apache Maven compatible with the project's `pom.xml`
- Git, if cloning the repository

Verify that Java is installed:

```bash
java -version
javac -version
```

### 1. Clone the Repository

```bash
git clone https://github.com/Himagna-Evuri/RedisLite.git
cd RedisLite
```

### 2. Open the Project

Open the project directory in IntelliJ IDEA and allow Maven to load the project dependencies.

Ensure the project SDK is configured to use JDK 21 or a compatible later version.

### 3. Build and Test

From the project root, run:

```bash
mvn clean test
```

This command cleans previous build output, compiles the project, and runs the automated tests.

If Maven is not available on your system's PATH, use IntelliJ IDEA's bundled Maven installation or run the tests through IntelliJ's Maven tool window.

### 4. Run the Application

In IntelliJ IDEA:

1. Navigate to `src/main/java/com/redislite/Main.java`.
2. Run the `main()` method.
3. Enter supported RedisLite commands in the application console.

The application uses `dump.rdb` for persistence. Keep track of this file when testing persistence and restart behavior.

## Example Session

A typical command-line session looks like this:

```text
SET name Himagna
OK

GET name
Himagna

SET session abc 10
OK

TTL session
9

EXISTS name
true

DEL name
true

GET name
null

FLUSHDB
OK

EXIT
```

The remaining TTL shown in this example is illustrative; the actual value depends on elapsed time.

## Testing

RedisLite uses JUnit 5 for automated testing.

The latest verified test run completed with:

```text
Tests run: 22
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

The test suite covers storage operations, TTL behavior, persistence-related behavior, expiration cleanup, and protection against removing replacement entries.

Run the tests with:

```bash
mvn clean test
```

The result above reflects the latest verified run and may change if the code or tests are modified.

## Design Decisions

### `ConcurrentHashMap` for In-Memory Storage

A concurrent map is appropriate for a store accessed by both the main application and the background expiry thread. Additional synchronization is still required for operations involving multiple steps or persistence.

### Absolute Expiration Timestamps

Storing an absolute expiration time allows RedisLite to check whether a key has expired without repeatedly tracking the elapsed lifetime.

### Lazy and Active Expiration

Lazy expiration checks entries when accessed, while active expiration periodically scans the store. Using both mechanisms helps remove expired entries even when no client accesses them.

### Separation of Responsibilities

Command parsing, command handling, storage, persistence, and expiration are organized into separate classes. This makes the implementation easier to understand, test, and extend.

### Temporary-File Replacement

Writing to a temporary file before replacing the main database file reduces the chance of corrupting the existing file during a failed write. It is a safeguard, not a complete crash-recovery protocol.

### Persistence Failure Handling

Storage mutations coordinate in-memory changes with persistence and can roll back changes when saving fails. This helps avoid reporting a successful persistent mutation when the corresponding save was unsuccessful.

## Limitations and Future Improvements

RedisLite is an educational implementation intended to demonstrate database concepts. It is not a replacement for Redis or a production database.

Potential improvements include:

- Reduce disk I/O by avoiding a complete database rewrite after every mutation.
- Add commands such as `INCR`, `DECR`, `APPEND`, and `MGET`.
- Support additional data structures, including lists, sets, and hashes.
- Add a TCP client-server architecture to support multiple clients.
- Improve persistence recovery and durability guarantees.
- Strengthen concurrency handling and define behavior for multiple processes.
- Add broader integration, failure-recovery, and concurrency tests.
- Make persistence and expiration-cleaner intervals configurable.
- Add benchmarking and performance measurements.
- Improve command validation and error reporting.

## Learning Outcomes

Building RedisLite provided hands-on experience with:

- Designing an in-memory key-value data structure.
- Using Java collections for concurrent access.
- Implementing TTL-based expiration.
- Coordinating a background cleanup thread.
- Serializing data and restoring it from disk.
- Handling persistence failures and temporary-file replacement.
- Separating application responsibilities through layered design.
- Writing automated tests with JUnit 5.
- Using Maven and Git to build, test, and maintain a Java project.

## Author

**Himagna Evuri**

Computer Science and Engineering Student

GitHub: [Himagna-Evuri](https://github.com/Him