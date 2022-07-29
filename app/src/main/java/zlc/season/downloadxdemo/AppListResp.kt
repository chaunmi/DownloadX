package zlc.season.downloadxdemo


import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Job
import zlc.season.downloadx.core.DownloadTask
import zlc.season.yasha.YashaItem

data class AppListResp(
    @SerializedName("obj")
    val appList: List<AppInfo> = listOf(),
) {
    data class AppInfo(

        @SerializedName("apkUrl")
        val apkUrl: String = "",

        @SerializedName("appName")
        val appName: String = "",

        @SerializedName("editorIntro")
        val editorIntro: String = "",

        @SerializedName("iconUrl")
        val iconUrl: String = "",
    ) : YashaItem {

        @Transient
        var progressJob: Job? = null
    }
}