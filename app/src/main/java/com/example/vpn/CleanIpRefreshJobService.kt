package com.example.vpn

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.data.local.CleanIpHealthStatus
import com.example.data.local.LocalVpnConfigRepository
import com.example.data.network.RealPingEngine
import com.example.vpn.engine.DynamicIpDnsRotator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val TAG = "CleanIpRefreshJob"

object CleanIpRefreshScheduler {
    const val JOB_ID = 44071
    private const val REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 hours

    fun schedulePeriodicRefresh(context: Context): Boolean {
        return try {
            val appCtx = context.applicationContext
            val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return false
            val existing = scheduler.getPendingJob(JOB_ID)
            if (existing != null) return true

            val component = ComponentName(appCtx, CleanIpRefreshJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(REFRESH_INTERVAL_MS)
                .build()
            scheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS
        } catch (t: Throwable) {
            Log.d(TAG, "Unable to schedule periodic Clean IP refresh: ${t.message}")
            false
        }
    }

    suspend fun refreshTopCandidatesNow(
        context: Context,
        limit: Int = 30
    ): Int = withContext(Dispatchers.IO) {
        val repo = LocalVpnConfigRepository(context.applicationContext)
        repo.seedCleanIpPoolIfNeeded()
        val entries = repo.getCleanIpPoolOnce()
        val candidates = entries
            .sortedWith(
                compareBy<com.example.data.local.CleanIpEntity> { it.lastCheckedAtMs }
                    .thenBy { if (it.latencyMs > 0) it.latencyMs else Int.MAX_VALUE }
            )
            .take(limit)

        if (candidates.isEmpty()) return@withContext 0

        val sem = Semaphore(6)
        val probeResults = coroutineScope {
            candidates.map { item ->
                async {
                    sem.withPermit {
                        val ping = RealPingEngine.ping(host = item.host, port = 443, timeoutMs = 1500)
                        item.host to ping
                    }
                }
            }.awaitAll()
        }

        val now = System.currentTimeMillis()
        val healthyHosts = mutableListOf<String>()
        for ((host, res) in probeResults) {
            repo.recordCleanIpHealth(host, res.isReachable, res.latencyMs, now)
            if (res.isReachable) healthyHosts.add(host)
        }
        if (healthyHosts.isNotEmpty()) {
            DynamicIpDnsRotator.seedCandidateIps(healthyHosts)
        }
        healthyHosts.size
    }
}

/**
 * Native Android JobService that periodically re-verifies Clean IP latency and reachability
 * in the background and keeps DynamicIpDnsRotator fed with fresh low-latency endpoints.
 */
class CleanIpRefreshJobService : JobService() {

    private val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        runningJob?.cancel()
        runningJob = jobScope.launch {
            try {
                val healthyCount = CleanIpRefreshScheduler.refreshTopCandidatesNow(applicationContext)
                Log.i(TAG, "Background Clean IP health refresh completed ($healthyCount reachable)")
            } catch (t: Throwable) {
                Log.w(TAG, "Background Clean IP health refresh error: ${t.message}")
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        runningJob?.cancel()
        return true
    }

    override fun onDestroy() {
        jobScope.cancel()
        super.onDestroy()
    }
}
