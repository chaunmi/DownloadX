package zlc.season.downloadx.net

import java.io.IOException

class RequestException internal constructor(
    val code: Int,
    detailMessage: String
) : IOException(detailMessage)