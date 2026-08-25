package com.towerbound.laststand.save

import android.content.Context
import android.util.Base64
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.meta.PlayerProfile
import com.towerbound.laststand.meta.QuestManager
import com.towerbound.laststand.meta.AchievementManager
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Robust versioned save system (design: SAVE SYSTEM).
 *
 *  - versioned JSON payload + SHA-256 checksum
 *  - atomic write (temp file + rename)
 *  - primary + backup + temp files
 *  - corruption detection with automatic backup recovery
 *  - forward migration hooks
 *  - run-in-progress snapshot for crash recovery
 */
class SaveManager(private val context: Context) {

    companion object {
        const val SAVE_VERSION = 1
        const val FILE_PRIMARY = "towerbound_save.json"
        const val FILE_BACKUP = "towerbound_save.bak"
        const val FILE_TEMP = "towerbound_save.tmp"
        const val FILE_RUN = "towerbound_run.json"

        const val KEY_VERSION = "version"
        const val KEY_CHECKSUM = "checksum"
        const val KEY_PAYLOAD = "payload"
    }

    data class LoadResult(val profile: PlayerProfile, val quests: QuestManager,
                          val achievements: AchievementManager,
                          val recoveredFromBackup: Boolean, val corrupted: Boolean)

    /** Persist profile/quests/achievements atomically with checksum. */
    fun save(profile: PlayerProfile, quests: QuestManager, achievements: AchievementManager) {
        achievements.persistState()
        val payload = JSONObject()
        payload.put("profile", profile.toJson())
        payload.put("quests", quests.toJson())
        payload.put("achievements", achievements.toJson())

        val envelope = JSONObject()
        envelope.put(KEY_VERSION, SAVE_VERSION)
        envelope.put(KEY_PAYLOAD, payload.toString())
        envelope.put(KEY_CHECKSUM, checksum(payload.toString()))

        val data = envelope.toString().toByteArray(Charsets.UTF_8)
        val dir = context.filesDir
        try {
            // backup the previous good save
            val primary = File(dir, FILE_PRIMARY)
            if (primary.exists()) {
                primary.copyTo(File(dir, FILE_BACKUP), overwrite = true)
            }
            // atomic write: temp then rename
            val tmp = File(dir, FILE_TEMP)
            tmp.writeBytes(data)
            if (tmp.renameTo(primary)) {
                GameLog.d("Save", "saved ${data.size} bytes (v$SAVE_VERSION)")
            } else {
                // rename can fail across mount points; fall back to copy
                tmp.copyTo(primary, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            GameLog.e("Save", "save failed", t)
        }
    }

    /** Load with corruption detection + backup recovery + migration. */
    fun load(): LoadResult {
        val profile = PlayerProfile()
        val quests = QuestManager()
        val achievements = AchievementManager()
        quests.ensureDailySet()

        val dir = context.filesDir
        val primary = File(dir, FILE_PRIMARY)

        var recovered = false
        var corrupted = false

        var loaded = readAndVerify(primary)
        if (loaded == null && primary.exists()) {
            corrupted = true
            GameLog.w("Save", "primary save corrupted — trying backup")
            val backup = File(dir, FILE_BACKUP)
            loaded = readAndVerify(backup)
            if (loaded != null) recovered = true
        }
        if (loaded == null) {
            // fresh start (no file, or both corrupt)
            return LoadResult(profile, quests, achievements, recovered, corrupted)
        }

        try {
            val payload = JSONObject(loaded)
            profile.fromJson(payload.optJSONObject("profile") ?: JSONObject())
            quests.fromJson(payload.optJSONObject("quests") ?: JSONObject())
            achievements.fromJson(payload.optJSONObject("achievements") ?: JSONObject())
        } catch (t: Throwable) {
            GameLog.e("Save", "payload parse failed", t)
            corrupted = true
        }
        return LoadResult(profile, quests, achievements, recovered, corrupted)
    }

    /** Returns the payload string when the envelope verifies, else null. */
    private fun readAndVerify(file: File): String? {
        if (!file.exists()) return null
        return try {
            val text = file.readText(Charsets.UTF_8)
            val envelope = JSONObject(text)
            val version = envelope.optInt(KEY_VERSION, -1)
            if (version < 0 || version > SAVE_VERSION) return null
            val payloadStr = envelope.optString(KEY_PAYLOAD)
            val checksum = envelope.optString(KEY_CHECKSUM)
            if (checksum(payloadStr) != checksum) {
                GameLog.w("Save", "checksum mismatch in ${file.name}")
                return null
            }
            // migration chain
            var migrated = payloadStr
            var v = version
            while (v < SAVE_VERSION) {
                migrated = migrate(migrated, v)
                v++
            }
            migrated
        } catch (t: Throwable) {
            GameLog.e("Save", "read failed for ${file.name}", t)
            null
        }
    }

    /** Version-to-version payload migrations. */
    private fun migrate(payload: String, fromVersion: Int): String {
        // v1 is current; future versions chain here
        GameLog.i("Save", "migrating save from v$fromVersion")
        return payload
    }

    private fun checksum(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /* ---------------- run snapshot (crash recovery) ---------------- */

    /** Save an in-progress run snapshot for resume. */
    fun saveRunSnapshot(runJson: JSONObject) {
        try {
            val dir = context.filesDir
            val tmp = File(dir, FILE_RUN + ".tmp")
            tmp.writeText(runJson.toString(), Charsets.UTF_8)
            tmp.renameTo(File(dir, FILE_RUN))
        } catch (t: Throwable) {
            GameLog.e("Save", "run snapshot failed", t)
        }
    }

    fun loadRunSnapshot(): JSONObject? {
        return try {
            val file = File(context.filesDir, FILE_RUN)
            if (!file.exists()) null else JSONObject(file.readText(Charsets.UTF_8))
        } catch (t: Throwable) {
            null
        }
    }

    fun clearRunSnapshot() {
        try {
            File(context.filesDir, FILE_RUN).delete()
        } catch (_: Throwable) {}
    }
}
