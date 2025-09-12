# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AnyStream is a self-hosted streaming service for media collections built with Kotlin Multiplatform. It consists of a Ktor-based server and multiple client implementations using Compose Multiplatform.

## Architecture

### Server Components
- **server/application** - Main web server built with Ktor, serves API and web client
- **server/library-manager** - Manages media library scanning and organization
- **server/metadata-manager** - Handles media metadata fetching (TMDB integration)
- **server/stream-service** - Media streaming and transcoding with FFmpeg
- **server/db-models** - Database schema, migrations (Flyway), and jOOQ-generated models
- **server/shared** - Shared server utilities and common code

### Client Components
- **client/data-models** - Shared data models between server and all clients (uses Poko for data classes)
- **client/core** - Multiplatform client infrastructure built with Mobius.kt
- **client/presentation** - Shared presentation logic across clients
- **client/ui** - Shared Compose Multiplatform UI components
- **client/web** - Web client implementation with Compose HTML
- **client/android** - Android app with Jetpack Compose
- **client/ios** - iOS app with Compose Multiplatform + SwiftUI
- **client/desktop** - Desktop app with Compose Multiplatform (experimental)

### Database
- Uses SQLite as the single database with Flyway migrations
- jOOQ provides typesafe SQL DSL
- Single Table Inheritance pattern for media metadata
- Migration files: `server/db-models/src/main/resources/db/migration`

## Development Commands

### Building and Running
```bash
# Build entire project
./gradlew build

# Run server (includes web client build)
./gradlew :server:application:run

# Run web client in development mode with hot reload
./gradlew :client:web:jsBrowserRun

# Build production server JAR
./gradlew :server:application:shadowJar

# Run tests
./gradlew test

# Run tests for specific module
./gradlew :server:application:test

# Generate jOOQ database classes
./gradlew :server:db-models:jooq-generator:generateJooq
```

### Environment Variables
When running the server:
- `WEB_CLIENT_PATH` - Path to web client build output
- `DATABASE_URL` - Database file location (defaults to `./anystream.db`)

### Documentation
```bash
# Install MkDocs dependencies
pip install -r docs/requirements.txt

# Serve documentation locally
mkdocs serve

# View docs at http://127.0.0.1:8000
```

## Code Style and Patterns

### Kotlin Multiplatform Structure
- Server uses JVM target with Ktor framework
- Web client uses Kotlin/JS with Compose HTML
- Mobile clients use Kotlin Multiplatform with Compose Multiplatform
- Shared code in `client/core` and `client/data-models`

### Database Patterns
- Use jOOQ DSL for database queries
- Coroutines support for async database operations
- Flyway handles schema migrations automatically
- SQLite with full-text search (FTS5) and JSON support

### Dependency Injection
- Uses Koin for dependency injection across server and clients
- Configuration in respective build.gradle.kts files

### State Management
- Client state management with Mobius.kt (MVI pattern)
- Shared presentation logic in `client/presentation`

## Key Libraries and Frameworks

**Server:**
- Ktor (web framework)
- jOOQ (database queries)
- Flyway (migrations)  
- Koin (DI)
- TMDB API (metadata)
- Jaffree (FFmpeg wrapper)

**Clients:**
- Compose Multiplatform (UI)
- Mobius.kt (state management)
- Ktor Client (HTTP)
- kotlinx.serialization (JSON)

## Testing
- Test files alongside source in `src/test/kotlin`
- Uses Kotlin test framework
- Database testing utilities in `server/db-models/testing`
- Run with `./gradlew test` or `./gradlew :module:test`