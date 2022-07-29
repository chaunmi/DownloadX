package zlc.season.downloadxdemo

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

object AppInfoManager {
    interface Api {
        @GET
        suspend fun get(@Url url: String): AppListResp
    }

    private const val url = "https://android.myapp.com/myapp/union/apps.htm?unionId=12"

    private fun apiCreator(client: OkHttpClient): Api {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://www.example.com")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(Api::class.java)
    }

    suspend fun getAppInfoList(): List<AppListResp.AppInfo> {
        return listOf(AppListResp.AppInfo(apkUrl = "https://cdn.llscdn.com/yy/files/xs8qmxn8-lls-LLS-5.8-800-20171207-111607.apk", appName = "流利说英语5.8"
                    , iconUrl = "https://img0.baidu.com/it/u=494881638,1361777064&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=281", editorIntro = "流利说英语说明5.8"),
                      AppListResp.AppInfo(apkUrl = "https://cdn.llscdn.com/yy/files/tkzpx40x-lls-LLS-5.7-785-20171108-111118.apk", appName = "流利说英语5.7"
                      , iconUrl = "https://img0.baidu.com/it/u=4265149218,1786155796&fm=253&fmt=auto&app=138&f=JPEG?w=360&h=240", editorIntro = "流利说英语说明5.7版本"),
                     AppListResp.AppInfo(apkUrl = "http://dldir1.qq.com/weixin/android/weixin703android1400.apk", appName = "微信",
                     iconUrl = "https://img0.baidu.com/it/u=559165934,420824754&fm=253&fmt=auto&app=138&f=PNG?w=500&h=500", editorIntro = "微信安装包"),
                    AppListResp.AppInfo(apkUrl = "https://qd.myapp.com/myapp/qqteam/AndroidQQ/mobileqq_android.apk", appName = "QQ",
                    iconUrl = "https://img2.baidu.com/it/u=2655029475,2190949369&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=593", editorIntro = "QQ安装包"),
                    AppListResp.AppInfo(apkUrl = "http://wap.dl.pinyin.sogou.com/wapdl/hole/201512/03/SogouInput_android_v7.11_sweb.apk", appName = "搜狗",
                    iconUrl = "https://img1.baidu.com/it/u=360492046,2535032273&fm=253&fmt=auto&app=138&f=PNG?w=256&h=256", editorIntro = "sougou输入法安装包"))
    //    return apiCreator(OkHttpClient().newBuilder().build()).get(url).appList
    }
}