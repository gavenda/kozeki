package dev.gavenda.kozeki.data.files

import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * EPUBs another app hands over, by opening one with Kozeki or by sharing some to it. They arrive
 * at the activity, possibly in a fresh process and with any screen showing, so they wait here for
 * the library to take them in. The book that is then to be opened is passed back the same way.
 */
class IncomingEpubs {

    /**
     * @param open The file was opened rather than shared, which asks for its book to be opened for
     *   reading once it is in the library.
     */
    data class Request(val uris: List<Uri>, val open: Boolean)

    private val queue = MutableStateFlow<List<Request>>(emptyList())

    /** What has arrived and has not been taken yet, oldest first. */
    val waiting: StateFlow<List<Request>> = queue.asStateFlow()

    private val openChannel = Channel<String>(Channel.BUFFERED)

    /** The books to open, each as soon as its file has been imported. */
    val toOpen: Flow<String> = openChannel.receiveAsFlow()

    fun add(request: Request) = queue.update { it + request }

    /** Hands out the oldest request and forgets it, so it is imported once however many ask for it. */
    fun take(): Request? {
        while (true) {
            val waiting = queue.value
            val next = waiting.firstOrNull() ?: return null
            if (queue.compareAndSet(waiting, waiting.drop(1))) return next
        }
    }

    fun open(bookId: String) {
        openChannel.trySend(bookId)
    }
}
