package zlc.season.downloadx.core

import zlc.season.downloadx.Progress
import zlc.season.downloadx.State

interface IDownloadListener {
    fun onProgress(
        progress: Progress
    ) {}

    fun onStateChange(state: State) {}
}