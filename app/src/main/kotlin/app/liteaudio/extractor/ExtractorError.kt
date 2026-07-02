package app.liteaudio.extractor

import app.liteaudio.R
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

/**
 * Error taxonomy for everything YouTube can throw at us.
 * [retryable] errors feed the infinite-retry loops; the rest are per-item verdicts.
 */
sealed class ExtractorError(
    val stringRes: Int,
    val retryable: Boolean,
    cause: Throwable? = null,
) : Exception(cause) {

    class GeoBlocked(cause: Throwable? = null) : ExtractorError(R.string.err_geo_blocked, false, cause)
    class AgeRestricted(cause: Throwable? = null) : ExtractorError(R.string.err_age_restricted, false, cause)
    class Private(cause: Throwable? = null) : ExtractorError(R.string.err_private, false, cause)
    class Deleted(cause: Throwable? = null) : ExtractorError(R.string.err_deleted, false, cause)
    class RateLimited(cause: Throwable? = null) : ExtractorError(R.string.err_rate_limited, true, cause)

    /** The canary: NewPipeExtractor no longer understands YouTube's markup. */
    class ParseBroken(cause: Throwable? = null) : ExtractorError(R.string.err_parse_broken, false, cause)

    class Network(cause: Throwable? = null) : ExtractorError(R.string.err_network, true, cause)

    /** Marker string persisted into tracks.unavailableReason. */
    val marker: String
        get() = when (this) {
            is GeoBlocked -> "GEO_BLOCKED"
            is AgeRestricted -> "AGE_RESTRICTED"
            is Private -> "PRIVATE"
            is Deleted -> "DELETED"
            is RateLimited -> "RATE_LIMITED"
            is ParseBroken -> "PARSE_BROKEN"
            is Network -> "NETWORK"
        }

    companion object {
        fun from(t: Throwable): ExtractorError = when (t) {
            is ExtractorError -> t
            is GeographicRestrictionException -> GeoBlocked(t)
            is AgeRestrictedContentException -> AgeRestricted(t)
            is PrivateContentException -> Private(t)
            is PaidContentException -> Private(t)
            is AccountTerminatedException -> Deleted(t)
            is ContentNotAvailableException -> Deleted(t)
            is ReCaptchaException -> RateLimited(t)
            is IOException -> Network(t)
            is ExtractionException -> {
                // an IO problem may hide behind a parse wrapper
                val root = generateSequence(t as Throwable) { it.cause }.last()
                if (root is IOException) Network(t) else ParseBroken(t)
            }
            else -> ParseBroken(t)
        }
    }
}
