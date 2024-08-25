package anystream.util

import anystream.models.MediaKind


val MediaKind.bootstrapIcon: String
    get() = when (this) {
        MediaKind.MOVIE -> "bi-film"
        MediaKind.TV -> "bi-tv"
        MediaKind.MUSIC -> "bi-music-note-beamed"
        else -> "bi-question-lg"
    }