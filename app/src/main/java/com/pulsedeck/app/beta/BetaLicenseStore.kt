package com.pulsedeck.app.beta

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

class BetaLicenseStore(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = sharedDataStore(appContext)

    suspend fun load(): StoredBetaLicenseSnapshot {
        val prefs = dataStore.data.first()
        val license = prefs[LICENSE_JSON]?.let { raw ->
            runCatching { BetaLicenseCodec.decode(raw) }.getOrNull()
        }
        return StoredBetaLicenseSnapshot(
            license = license,
            lastVerifiedServerTimeEpochMs = prefs[LAST_VERIFIED_SERVER_TIME],
            lastSuccessfulRefreshEpochMs = prefs[LAST_SUCCESSFUL_REFRESH],
            lastRefreshStatus = BetaLicenseCodec.enumValue(
                prefs[LAST_REFRESH_STATUS],
                BetaLicenseStatus.Unknown,
            ),
        )
    }

    suspend fun saveLicense(
        license: BetaLicense,
        serverTimeEpochMs: Long,
        refreshStatus: BetaLicenseStatus = license.status,
        refreshedAtEpochMs: Long = System.currentTimeMillis(),
    ) {
        dataStore.edit { prefs ->
            prefs[LICENSE_JSON] = BetaLicenseCodec.encode(license).toString()
            prefs[LAST_VERIFIED_SERVER_TIME] = serverTimeEpochMs
            prefs[LAST_SUCCESSFUL_REFRESH] = refreshedAtEpochMs
            prefs[LAST_REFRESH_STATUS] = refreshStatus.name
            prefs.remove(LAST_INVITE_ENTRY)
        }
    }

    suspend fun saveRefreshStatus(status: BetaLicenseStatus, serverTimeEpochMs: Long? = null) {
        dataStore.edit { prefs ->
            prefs[LAST_REFRESH_STATUS] = status.name
            if (serverTimeEpochMs != null) prefs[LAST_VERIFIED_SERVER_TIME] = serverTimeEpochMs
        }
    }

    suspend fun clearLocalLicenseForTestsOnly() {
        dataStore.edit { prefs ->
            prefs.remove(LICENSE_JSON)
            prefs.remove(LAST_VERIFIED_SERVER_TIME)
            prefs.remove(LAST_SUCCESSFUL_REFRESH)
            prefs.remove(LAST_REFRESH_STATUS)
            prefs.remove(LAST_INVITE_ENTRY)
        }
    }

    companion object {
        private val LICENSE_JSON = stringPreferencesKey("license_json")
        private val LAST_VERIFIED_SERVER_TIME = longPreferencesKey("last_verified_server_time_epoch_ms")
        private val LAST_SUCCESSFUL_REFRESH = longPreferencesKey("last_successful_refresh_epoch_ms")
        private val LAST_REFRESH_STATUS = stringPreferencesKey("last_refresh_status")
        private val LAST_INVITE_ENTRY = stringPreferencesKey("last_invite_entry_removed")

        @Volatile
        private var dataStoreInstance: DataStore<Preferences>? = null

        fun sharedDataStore(context: Context): DataStore<Preferences> {
            val appContext = context.applicationContext
            return dataStoreInstance ?: synchronized(this) {
                dataStoreInstance ?: PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    produceFile = { appContext.preferencesDataStoreFile("pulsedeck_beta_license.preferences_pb") },
                ).also { dataStoreInstance = it }
            }
        }
    }
}
