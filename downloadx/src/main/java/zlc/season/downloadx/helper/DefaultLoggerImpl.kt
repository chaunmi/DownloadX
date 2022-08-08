package zlc.season.downloadx.helper

import android.util.Log

class DefaultLoggerImpl: ILogger {
    override fun e(tag: String?, msg: String?, tr: Throwable?) {
        if (LogUtils.DEBUG) {
            Log.e(tag, msg, tr)
        }
    }

    override fun d(tag: String?, msg: String?, tr: Throwable?) {
        if (LogUtils.DEBUG) {
            Log.d(tag, msg, tr)
        }
    }

    override fun i(tag: String?, msg: String?, tr: Throwable?) {
        if (LogUtils.DEBUG) {
            Log.i(tag, msg, tr)
        }
    }

    override fun w(tag: String?, msg: String?, tr: Throwable?) {
        if (LogUtils.DEBUG) {
            Log.w(tag, msg, tr)
        }
    }
}