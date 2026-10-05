package dev.gavenda.kozeki.ui

import androidx.annotation.StringRes
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider

/** Why a metadata lookup produced nothing, in terms the user can act on. */
enum class LookupError(@param:StringRes val message: Int) {
    OFFLINE(R.string.lookup_error_offline),
    RATE_LIMITED(R.string.lookup_error_rate_limited),
    SIGNED_OUT(R.string.lookup_error_signed_out),
    UNAUTHORIZED(R.string.lookup_error_unauthorized),

    /** The source wants an API key and this build has none. */
    NOT_CONFIGURED(R.string.lookup_error_not_configured),

    /** This build has an API key, and the source will not take it. */
    KEY_REJECTED(R.string.lookup_error_key_rejected),

    /** Signed in, but before the app asked for what this lookup needs. */
    MISSING_PERMISSION(R.string.lookup_error_missing_permission),
    OTHER(R.string.lookup_error_other),
    ;

    companion object {
        fun from(error: Throwable): LookupError = when (error) {
            is MetadataException.Network -> OFFLINE
            is MetadataException.RateLimited -> RATE_LIMITED
            is MetadataException.Unauthorized -> UNAUTHORIZED
            is MetadataException.KeyRejected -> KEY_REJECTED
            is MetadataException.MissingScope -> MISSING_PERMISSION
            is MetadataException.Unavailable -> from(error.reason)
            else -> OTHER
        }

        fun from(reason: MetadataProvider.Unavailable): LookupError = when (reason) {
            MetadataProvider.Unavailable.SIGNED_OUT -> SIGNED_OUT
            MetadataProvider.Unavailable.NOT_CONFIGURED -> NOT_CONFIGURED
        }
    }
}
