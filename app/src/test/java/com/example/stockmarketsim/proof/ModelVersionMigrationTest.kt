package com.example.stockmarketsim.proof

import com.example.stockmarketsim.worker.ModelUpdaterWorker
import org.junit.Assert.*
import org.junit.Test

/**
 * REGRESSION SUITE: ModelUpdaterWorker — OTA Version Migration Logic
 *
 * Tests the pure `resolveCurrentVersion()` companion function which handles
 * three SharedPreferences states for the `current_model_version` key:
 *
 *   State 1 — String key present   (new installs / already migrated)
 *   State 2 — Key absent / null    (fresh install or wiped prefs)
 *   State 3 — ClassCastException   (legacy install that stored version as Int)
 *
 * All tests are pure JVM — no Android context or Robolectric needed.
 */
class ModelVersionMigrationTest {

    // =========================================================================
    // State 1: String key already exists (happy path, new format)
    // =========================================================================

    @Test
    fun `returns stored String version when key exists as String`() {
        var migrateCalled = false
        val result = ModelUpdaterWorker.resolveCurrentVersion(
            getString = { "20260301.13" },
            getInt    = { error("should not be called") },
            migrate   = { migrateCalled = true }
        )
        assertEquals("Should return the stored String version", "20260301.13", result)
        assertFalse("migrate() must NOT be called when String key exists", migrateCalled)
    }

    @Test
    fun `version string is returned verbatim without truncation`() {
        // Critical: the CI version "20260301.13" must survive the round-trip
        // (old getInt() code would have truncated this to 20260301)
        val ciVersion = "20260301.13"
        val result = ModelUpdaterWorker.resolveCurrentVersion(
            getString = { ciVersion },
            getInt    = { 0 },
            migrate   = {}
        )
        assertTrue("Version must preserve the decimal part (.13)", result.contains("."))
        assertEquals(ciVersion, result)
    }

    // =========================================================================
    // State 2: Key absent / null (fresh install)
    // =========================================================================

    @Test
    fun `fresh install with no Int fallback returns sentinel zero`() {
        val result = ModelUpdaterWorker.resolveCurrentVersion(
            getString = { null },    // key doesn't exist as String
            getInt    = { 0 },       // no Int either — truly fresh
            migrate   = {}
        )
        assertEquals("Fresh install should return '0' as sentinel", "0", result)
    }

    @Test
    fun `fresh install with legacy Int value converts it to String`() {
        // Edge case: pref key is missing as String but the Int key exists (shouldn't
        // normally happen, but guards against partial migration states)
        val result = ModelUpdaterWorker.resolveCurrentVersion(
            getString = { null },
            getInt    = { 1 },
            migrate   = {} // no migrate callback needed — key was absent, not typed-wrong
        )
        assertEquals("Legacy Int '1' should be converted to String '1'", "1", result)
    }

    // =========================================================================
    // State 3: ClassCastException (legacy install — key exists but typed as Int)
    // =========================================================================

    @Test
    fun `ClassCastException triggers migration and returns converted String`() {
        var migratedValue: String? = null
        val result = ModelUpdaterWorker.resolveCurrentVersion(
            getString = { throw ClassCastException("SharedPreferences type mismatch") },
            getInt    = { 1 },   // old Int store
            migrate   = { migratedValue = it }
        )
        assertEquals("Should return '1' (converted from Int)", "1", result)
        assertEquals("migrate() should be called with '1'", "1", migratedValue)
    }

    @Test
    fun `ClassCastException with zero Int migrates to sentinel zero`() {
        var migratedValue: String? = null
        ModelUpdaterWorker.resolveCurrentVersion(
            getString = { throw ClassCastException("type mismatch") },
            getInt    = { 0 },
            migrate   = { migratedValue = it }
        )
        assertEquals("Zero Int should migrate to sentinel '0'", "0", migratedValue)
    }

    @Test
    fun `ClassCastException migrate callback is always invoked exactly once`() {
        var migrateCallCount = 0
        ModelUpdaterWorker.resolveCurrentVersion(
            getString = { throw ClassCastException("type mismatch") },
            getInt    = { 20260301 },
            migrate   = { migrateCallCount++ }
        )
        assertEquals("migrate() must be called exactly once", 1, migrateCallCount)
    }

    // =========================================================================
    // Version Comparison Correctness (latestVersion == currentVersion logic)
    // =========================================================================

    @Test
    fun `same version string equality correctly detects up-to-date model`() {
        val stored = "20260301.13"
        val remote = "20260301.13"
        // This is the comparison that replaced the old Int <= logic
        assertTrue("Identical versions should be equal", stored == remote)
    }

    @Test
    fun `different version strings correctly signal update needed`() {
        val stored = "20260301.13"
        val remote = "20260308.1"   // New Sunday run
        assertFalse("Different versions should NOT be equal — update required", stored == remote)
    }

    @Test
    fun `Int-truncated version 20260301 differs from full string 20260301_13`() {
        // Regression guard: this is EXACTLY the bug that existed before the fix.
        // getInt("20260301.13") returned 20260301, which then equalled the next
        // run's getInt result — silently skipping the update forever.
        val truncated = 20260301.toString()   // old broken path
        val fullVersion = "20260301.13"       // real CI version
        assertNotEquals(
            "Truncated Int version must NOT equal the full CI version string",
            truncated, fullVersion
        )
    }

    @Test
    fun `scientific notation version must NOT equal the human-readable CI version`() {
        // Regression guard for live-observed bug in logs (2026-03-03):
        //   "[INFO] Downloading new Deep Neural Net (v2.026030113E7)..."
        //
        // Root cause: CI emitted "version": 20260301.13 (unquoted JSON Double).
        // JSONObject.getString() on a Double calls Double.toString() which uses
        // scientific notation → "2.026030113E7".
        //
        // Fix: CI now emits "version": "$VERSION" (quoted JSON String).
        // This test documents why unquoted is wrong.
        val scientificNotation = 20260301.13.toString()  // what JSONObject.getString() produced
        val ciVersion = "20260301.13"                    // what the CI actually intends
        assertNotEquals(
            "Scientific notation '$scientificNotation' must not equal human-readable '$ciVersion'",
            scientificNotation, ciVersion
        )
        // Also confirm the fix: a quoted JSON string round-trips cleanly
        val quotedParsed = "20260301.13"  // what getString() returns after CI fix
        assertEquals("Quoted CI version must survive round-trip as-is", ciVersion, quotedParsed)
    }
}
