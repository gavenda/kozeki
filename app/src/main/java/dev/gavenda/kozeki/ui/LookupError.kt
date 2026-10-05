package dev.gavenda.kozeki.ui

import androidx.annotation.StringRes
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider

/** Why a metadata lookup produced nothing, in terms the user can act on. */
enum class LookupError(@param:StringRes val message: Int) {
    OFFLINE(R.string.lookup_error_offline),
    RATE_LIMITED(R.string.lookup_error_rate_limited),
    NOT_CONFIGURED(R.string.lookup_error_not_configured),
    SIGNED_OUT(R.string.lookup_error_signed_out),
    UNAUTHORIZED(R.string.lookup_error_unauthorized),
    OTHER(R.string.lookup_error_other),
    ;

    companion object {
        fun from(error: Throwable): LookupError = when (error) {
            is MetadataException.Network -> OFFLINE
            is MetadataException.RateLimited -> RATE_LIMITED
            is MetadataException.Unauthorized -> UNAUTHORIZED
            is MetadataException.Unavailable -> from(error.reason)
            else -> OTHER
        }

        fun from(reason: MetadataProvider.Unavailable): LookupError = when (reason) {
            MetadataProvider.Unavailable.NOT_CONFIGURED -> NOT_CONFIGURED
            MetadataProvider.Unavailable.SIGNED_OUT -> SIGNED_OUT
        }
    }
}
