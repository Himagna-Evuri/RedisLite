# RedisLite

RedisLite is a lightweight, Redis-inspired in-memory key-value store built in Java.

The project implements core key-value operations along with TTL-based key expiration, background expiry cleanup, file-based persistence, command parsing, and automated testing.

It was built as a hands-on project to understand how an in-memory database works internally, including data storage, expiration, persistence, concurrency, and command processing.

## Features

- In-memory key-value storage using `ConcurrentHashMap`
- `SET` and `GET` operations
- `DEL` and `EXISTS` operations
- TTL (Time-To-Live) support
- Automatic background removal of expired keys
- `KEYS` command to retrieve active keys
- `FLUSHDB`-style database clearing
- File-based persistence using `dump.rdb`
- Persistence across application restarts
- Safe persistence using temporary-file replacement
- Support for commas and backslashes in keys and values
- Command parsing and command handling layers
- JUnit 5 automated tests
- Maven build and test support

## Supported Commands

RedisLite provides a simple command-line interface inspired by Redis.

| Command | Description | Example |
|---|---|---|
| `SET` | Stores a key-value pair | `SET name Himagna` |
| `SET` with TTL | Stores a key-value pair with expiration | `SET session abc 10` |
| `GET` | Retrieves the value of a key | `GET name` |
| `DEL` | Deletes a key | `DEL name` |
| `EXISTS` | Checks whether a key exists | `EXISTS name` |
| `TTL` | Returns the remaining TTL of a key | `TTL session` |
| `KEYS` | Returns all active keys | `KEYS` |
| `FLUSHDB` | Removes all stored keys | `FLUSHDB` |
| `EXIT` | Closes RedisLite | `EXIT` |

### TTL Return Values

The `TTL` command follows Redis-style return values:

- `-1` — Key exists but has no expiration
- `-2` — Key does not exist or has expired
- Non-negative value — Remaining TTL in seconds

## Architecture

RedisLite follows a layered architecture:

```text
User
 │
 ▼
Main
 │
 ▼
CommandParser
 │
 ▼
CommandHandler
 │
 ▼
KeyValueStore
 ├── In-memory storage
 │     └── ConcurrentHashMap
 │
 ├── TTL management
 │     └── Background expiry thread
 │
 └── Persistence
       └── PersistenceManager
             │
             ▼
          dump.rdb
```

### Components

#### `Main`

Acts as the entry point of the application. It reads commands from the command line and passes them through the parser and command handler.

#### `CommandParser`

Parses user input into individual command arguments and normalizes the command name to uppercase.

#### `CommandHandler`

Handles commands such as `SET`, `GET`, `DEL`, `TTL`, `KEYS`, and `FLUSHDB` and connects them to the corresponding `KeyValueStore` operations.

#### `KeyValueStore`

Contains the core database logic.

It manages key-value pairs using a thread-safe `ConcurrentHashMap` and handles storing, retrieving, deleting, checking, and clearing keys.

It also contains the logic for checking expired keys and running the background expiry thread.

#### `PersistenceManager`

Responsible for saving the current database state to `dump.rdb` and loading it when RedisLite starts.

It also performs safe file replacement using a temporary file.

#### `Entry`

Represents a stored record containing:

- Value
- Optional expiration timestamp

## TTL Mechanism

RedisLite supports expiration of keys using Time-To-Live (TTL).

When a key is stored with a TTL, RedisLite calculates an absolute expiration timestamp:

```text
expiryTime = currentTime + (ttlSeconds × 1000)
```

The timestamp is stored along with the value in the `Entry` object.

### Expiration Strategy

RedisLite uses two mechanisms to handle expired keys:

1. **Lazy expiration** — expired keys are detected when they are accessed.
2. **Active expiration** — a background thread periodically scans and removes expired entries.

```text
SET session abc 10
        │
        ▼
Calculate expiry timestamp
        │
        ▼
Store Entry(value, expiryTime)
        │
        ▼
Background expiry thread
        │
        ▼
Check periodically
        │
        ▼
Remove expired key
```

This combination prevents expired keys from remaining in memory indefinitely.

## Persistence

RedisLite provides file-based persistence so that data can survive application restarts.

The database state is stored in:

```text
dump.rdb
```

### Saving Data

Whenever the database is modified through operations such as `SET`, `DEL`, or `FLUSHDB`, the current state is written to disk by the `PersistenceManager`.

The stored format is:

```text
key,value,expiryTime
```

For example:

```text
name,Himagna,null
session,abc,1750000000000
```

Keys and values containing commas or backslashes are escaped before being written to the file.

### Loading Data

When RedisLite starts:

```text
Application starts
      │
      ▼
Create database file if necessary
      │
      ▼
Load existing data from dump.rdb
      │
      ▼
Populate ConcurrentHashMap
      │
      ▼
Start background expiry thread
```

This allows previously stored data to be available after restarting the application.

### Safe File Replacement

RedisLite first writes database updates to a temporary file:

```text
dump.rdb.tmp
```

After the write succeeds, the temporary file replaces the existing database file.

This reduces the risk of leaving the main database file partially written if a write operation fails.

## Thread Safety

RedisLite uses `ConcurrentHashMap` as the underlying in-memory data structure.

This allows the main application thread and the background expiry thread to access the store concurrently.

The persistence write operation is synchronized so that multiple threads cannot execute the persistence operation simultaneously.

## Project Structure

```text
RedisLite/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── redislite/
│   │               ├── Main.java
│   │               │
│   │               ├── model/
│   │               │   └── Entry.java
│   │               │
│   │               ├── parser/
│   │               │   ├── CommandParser.java
│   │               │   └── CommandHandler.java
│   │               │
│   │               ├── storage/
│   │               │   └── KeyValueStore.java
│   │               │
│   │               ├── persistence/
│   │               │   └── PersistenceManager.java
│   │               │
│   │               └── ttl/
│   │                   └── ExpiryCleaner.java
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── redislite/
│                   └── KeyValueStoreTest.java
│
├── pom.xml
├── .gitignore
└── README.md
```

## How to Run

### Prerequisites

- Java JDK 21 or later
- IntelliJ IDEA or another Java IDE
- Maven

### Run Using IntelliJ IDEA

1. Open the RedisLite project in IntelliJ IDEA.
2. Make sure JDK 21 is configured.
3. Open `Main.java`.
4. Run the `main()` method.
5. Enter RedisLite commands in the terminal.

### Build and Test

Run:

```bash
mvn clean test
```

Expected result:

```text
Tests run: 7
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

### Example Session

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
```

Use:

```text
EXIT
```

to close RedisLite.

## Testing

RedisLite uses JUnit 5 for automated testing.

The test suite currently verifies:

- `SET` and `GET`
- `DELETE`
- `EXISTS`
- TTL behavior and automatic expiration
- `KEYS`
- `FLUSHDB`
- Data persistence across application restart

Current test result:

```text
7 tests
7 passed
0 failures
0 errors
```

## Design Decisions

### `ConcurrentHashMap`

A `ConcurrentHashMap` is used for in-memory storage because RedisLite has a background thread that removes expired entries while the main application may simultaneously access the store.

### Absolute Expiration Timestamp

Instead of storing the remaining TTL, RedisLite stores an absolute expiration timestamp.

Expiration can be checked using:

```text
currentTime >= expiryTime
```

### Lazy + Active Expiration

RedisLite combines:

- **Lazy expiration** — expired keys are detected when accessed.
- **Active expiration** — a background thread periodically removes expired keys.

### Separate Persistence Layer

Persistence logic is isolated inside `PersistenceManager` rather than being embedded entirely within `KeyValueStore`.

This keeps storage and persistence responsibilities separated.

### Temporary File Replacement

Database updates are first written to a temporary file and then moved over the main database file.

This helps reduce the possibility of leaving `dump.rdb` partially written after a failed write.

### Escaped File Format

Keys and values are escaped before being stored because commas are used as field separators in the persistence format.

This allows keys and values containing commas or backslashes to be stored and reconstructed correctly.

## Limitations and Future Improvements

RedisLite is an educational Redis-inspired implementation and is not intended to replace production database systems.

Potential improvements include:

- Move the expiry-cleaning logic completely into the `ExpiryCleaner` class
- Improve persistence performance by avoiding a full database rewrite after every modification
- Add more Redis-compatible commands such as `INCR`, `DECR`, `APPEND`, and `MGET`
- Add support for Lists, Sets, and Hashes
- Implement a client-server architecture using TCP sockets
- Add connection handling for multiple clients
- Improve concurrency control for persistence operations
- Add more comprehensive unit and integration tests
- Add configurable persistence and expiry-cleaner intervals

## Technologies Used

- **Java 21** — Core programming language
- **Maven** — Project management and build automation
- **JUnit 5** — Automated testing
- **ConcurrentHashMap** — Thread-safe in-memory storage
- **Java I/O / NIO** — File-based persistence
- **IntelliJ IDEA** — Development environment
- **Git** — Version control

## Author

**Himagna Evuri**

Computer Science & Engineering Student

This project was developed as a hands-on implementation to understand the internal concepts behind in-memory key-value stores, TTL-based expiration, persistence, concurrency, and command processing.