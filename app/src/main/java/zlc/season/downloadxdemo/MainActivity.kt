package zlc.season.downloadxdemo

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import zlc.season.bracer.start
import zlc.season.downloadx.Progress
import zlc.season.downloadx.State
import zlc.season.downloadx.core.DownloadConfig
import zlc.season.downloadx.core.DownloadManager
import zlc.season.downloadx.core.IDownloadListener
import zlc.season.downloadx.download
import zlc.season.downloadxdemo.databinding.ActivityMainBinding
import zlc.season.downloadxdemo.databinding.AppInfoItemBinding
import zlc.season.yasha.YashaDataSource
import zlc.season.yasha.YashaItem
import zlc.season.yasha.linear


class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val dataSource by lazy { AppListDataSource() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        dataSource.loading.onEach {
            binding.progress.visibility = if (it) View.VISIBLE else View.GONE
        }.launchIn(lifecycleScope)

        dataSource.retry.onEach {
            binding.btnRetry.visibility = if (it) View.VISIBLE else View.GONE
        }.launchIn(lifecycleScope)

        binding.btnRetry.setOnClickListener {
            dataSource.invalidate()
        }

        DownloadManager.init(context = this.applicationContext)

        binding.recyclerView.linear(dataSource) {
            renderBindingItem<AppListResp.AppInfo, AppInfoItemBinding> {
                onAttach {
                    val downloadTask = GlobalScope.download(data.apkUrl,
                        downloadConfig = DownloadConfig(downloadListener = object: IDownloadListener {
                            override fun onProgress(progress: Progress) {
                                itemBinding.button.updateProgress(progress)
                                progress.apply {
                                    if(totalSize > 0 && totalSize == downloadSize)
                                        Log.e("myTest", "========================== onProgress:  complete===================== ")
                                }
                            }

                            override fun onStateChange(state: State) {
                                itemBinding.button.setState(state)
                            }
                        }))
                }
                onBind {
                    itemBinding.title.text = data.appName
                    itemBinding.desc.text = data.editorIntro
                    itemBinding.icon.load(data.iconUrl)

                    itemBinding.root.setOnClickListener {
                        DetailActivity().apply {
                            appInfo = data
                        }.start(this@MainActivity)
                    }
                    itemBinding.button.setOnClickListener {
                        val downloadTask = GlobalScope.download(data.apkUrl)
                        when {
                            downloadTask.isSucceed() -> {
                                installApk(downloadTask.file()!!)
                            }
                            downloadTask.isStarted() -> {
                                downloadTask.stop()
                            }
                            else -> {
                                downloadTask.config.isRangeDownload = !binding.cbMultiThread.isChecked
                                downloadTask.start()
                            }
                        }
                    }
                }
            }
        }
    }

    class AppListDataSource : YashaDataSource() {
        val retry = MutableStateFlow(false)
        val loading = MutableStateFlow(false)

        override suspend fun loadInitial(): List<YashaItem> {
            return try {
                loading.value = true
                retry.value = false
                AppInfoManager.getAppInfoList()
            } catch (e: Exception) {
                e.printStackTrace()
                retry.value = true
                emptyList()
            } finally {
                loading.value = false
            }
        }
    }
}

