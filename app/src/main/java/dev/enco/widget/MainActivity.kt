package dev.enco.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.enco.widget.databinding.ActivityMainBinding
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val executor = Executors.newSingleThreadExecutor()
    private val refreshing = AtomicBoolean(false)
    private var askedForPermission = false
    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasBluetoothPermission()) {
            refresh()
        } else {
            binding.statusText.text = getString(R.string.permission_needed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.refreshButton.setOnClickListener {
            if (hasBluetoothPermission()) refresh() else askPermissions()
        }
        showInterval(BatteryStore.updateIntervalSeconds(this))
        showAppearance(BatteryStore.glass(this))
        binding.intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            onIntervalSelected(checkedId)
        }
    }

    override fun onResume() {
        super.onResume()
        render(BatteryStore.read(this))
        showInterval(BatteryStore.updateIntervalSeconds(this))
        showAppearance(BatteryStore.glass(this))
        if (!hasBluetoothPermission()) {
            if (!askedForPermission) {
                askedForPermission = true
                askPermissions()
            }
            return
        }
        refresh()
        if (BatteryStore.updateIntervalSeconds(this) > 0) startUpdates()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun refresh() {
        if (!refreshing.compareAndSet(false, true)) return
        binding.statusText.text = getString(R.string.checking)
        executor.execute {
            try {
                val reading = EncoRepository.refresh(this)
                runOnUiThread {
                    if (!isDestroyed) render(reading)
                }
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun render(reading: Reading) {
        bindLevel(binding.phoneValue, binding.phoneCharging, PhoneBattery.read(this), known = false)
        bindLevel(binding.leftValue, binding.leftCharging, reading.left, reading.known)
        bindLevel(binding.rightValue, binding.rightCharging, reading.right, reading.known)
        bindLevel(binding.caseValue, binding.caseCharging, reading.caseLevel, reading.known)
        binding.statusText.text = reading.status
    }

    private fun bindLevel(value: TextView, charging: TextView, level: Level?, known: Boolean) {
        if (level == null) {
            value.text = if (known) getString(R.string.off) else "—"
            charging.visibility = View.GONE
            return
        }
        value.text = "${level.percent}%"
        charging.visibility = if (level.charging) View.VISIBLE else View.GONE
    }

    private fun showInterval(seconds: Int) {
        binding.intervalGroup.setOnCheckedChangeListener(null)
        binding.intervalGroup.check(intervalId(seconds))
        binding.intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            onIntervalSelected(checkedId)
        }
    }

    private fun showAppearance(glass: Boolean) {
        binding.appearanceGroup.setOnCheckedChangeListener(null)
        binding.appearanceGroup.check(if (glass) R.id.appearance_glass else R.id.appearance_solid)
        binding.appearanceGroup.setOnCheckedChangeListener { _, checkedId ->
            BatteryStore.setGlass(this, checkedId == R.id.appearance_glass)
            EncoWidgetProvider.render(this)
        }
    }

    private fun onIntervalSelected(checkedId: Int) {
        val seconds = intervalSeconds(checkedId)
        if (seconds > 0 && !hasBluetoothPermission()) {
            showInterval(0)
            askPermissions()
            return
        }
        BatteryStore.setUpdateIntervalSeconds(this, seconds)
        if (seconds > 0) startUpdates() else stopService(Intent(this, EncoUpdateService::class.java))
    }

    private fun intervalId(seconds: Int): Int {
        return when (seconds) {
            60 -> R.id.interval_1
            300 -> R.id.interval_5
            900 -> R.id.interval_15
            1800 -> R.id.interval_30
            else -> R.id.interval_manual
        }
    }

    private fun intervalSeconds(checkedId: Int): Int {
        return when (checkedId) {
            R.id.interval_1 -> 60
            R.id.interval_5 -> 300
            R.id.interval_15 -> 900
            R.id.interval_30 -> 1800
            else -> 0
        }
    }

    private fun startUpdates() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, EncoUpdateService::class.java))
        } catch (error: Exception) {
            BatteryStore.setUpdateIntervalSeconds(this, 0)
            showInterval(0)
            binding.statusText.text = getString(R.string.background_blocked)
        }
    }

    private fun askPermissions() {
        permissions.launch(
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    private fun hasBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
}
