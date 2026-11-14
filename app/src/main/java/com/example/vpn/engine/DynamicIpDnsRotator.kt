package com.example.vpn.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "DynamicRotator"

/**
 * 10-Second Dynamic IP & DNS Auto-Rotation Engine.
 *
 * Automatically rotates the outbound proxy IP and DNS resolver every 10 seconds
 * to continuously bypass stateful firewall tracking, rate-limiting, and operator throttling in Iran.
 */
object DynamicIpDnsRotator {

    private val ipPool = ConfigAutoHealer.CLEAN_ANYCAST_IPS
    private val dnsPool = listOf("1.1.1.1", "8.8.8.8", "1.0.0.1", "9.9.9.9", "77.88.8.8")

    private var currentIpIndex = 0
    private var currentDnsIndex = 0

    private val _isAutoRotateEnabled = MutableStateFlow(true)
    val isAutoRotateEnabled: StateFlow<Boolean> = _isAutoRotateEnabled.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(10)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _currentCleanIp = MutableStateFlow(ipPool[0])
    val currentCleanIp: StateFlow<String> = _currentCleanIp.asStateFlow()

    private val _currentDns = MutableStateFlow(dnsPool[0])
    val currentDns: StateFlow<String> = _currentDns.asStateFlow()

    private val _rotationCount = MutableStateFlow(0L)
    val rotationCount: StateFlow<Long> = _rotationCount.asStateFlow()

    private var rotationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        startRotationLoop()
    }

    fun startRotationLoop() {
        rotationJob?.cancel()
        rotationJob = scope.launch {
            while (isActive) {
                delay(1000L)
                if (!_isAutoRotateEnabled.value) continue

                val currentRemaining = _countdownSeconds.value
                if (currentRemaining > 1) {
                    _countdownSeconds.value = currentRemaining - 1
                } else {
                    // 10 seconds elapsed: Rotate to next clean IP and DNS!
                    _countdownSeconds.value = 10
                    currentIpIndex = (currentIpIndex + 1) % ipPool.size
                    currentDnsIndex = (currentDnsIndex + 1) % dnsPool.size

                    val nextIp = ipPool[currentIpIndex]
                    val nextDns = dnsPool[currentDnsIndex]

                    _currentCleanIp.value = nextIp
                    _currentDns.value = nextDns
                    _rotationCount.value += 1

                    Log.d(TAG, "10s Dynamic Hop #$${_rotationCount.value}: Active IP=$nextIp, Active DNS=$nextDns")
                }
            }
        }
    }

    fun toggleAutoRotation() {
        val newState = !_isAutoRotateEnabled.value
        _isAutoRotateEnabled.value = newState
        if (newState) {
            _countdownSeconds.value = 10
        }
    }

    fun forceHopNow() {
        currentIpIndex = (currentIpIndex + 1) % ipPool.size
        currentDnsIndex = (currentDnsIndex + 1) % dnsPool.size
        _currentCleanIp.value = ipPool[currentIpIndex]
        _currentDns.value = dnsPool[currentDnsIndex]
        _countdownSeconds.value = 10
        _rotationCount.value += 1
    }

    fun getActiveOutboundIp(targetHost: String): String {
        // If target host is already a clean IP, or rotation is active, return current clean IP
        return if (_isAutoRotateEnabled.value) {
            _currentCleanIp.value
        } else {
            targetHost
        }
    }

    fun getActiveDnsServer(): String {
        return _currentDns.value
    }
}
