package com.union.campus

import android.os.Bundle
import android.view.WindowManager

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.union.campus.app.UnionApp
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.theme.UnionTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

class MainActivity : FragmentActivity() {

    private var biometricContinuation: kotlin.coroutines.Continuation<Boolean>? = null
    private var pausedAtMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边：系统栏透明、内容延伸至系统栏下方，insets 统一交给 Compose
        enableEdgeToEdge()

        MainActivityHolder.attach(this)

        setContent {
            val screenshotBlock by ServiceLocator.settings.screenshotBlock.collectAsState(initial = false)
            if (screenshotBlock) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val themeMode by ServiceLocator.settings.themeMode.collectAsState(initial = "system")
            val dynamicColor by ServiceLocator.settings.dynamicColor.collectAsState(initial = false)
            val presetColor by ServiceLocator.settings.themeColorPreset.collectAsState(initial = "teal")
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            UnionTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                presetColor = presetColor,
            ) {
                UnionApp()
            }
        }

        // 回到前台时的生物识别锁（离开超过 5 分钟）
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val settings = ServiceLocator.settings
                val enabled = settings.biometricUnlock.first() && settings.autoLock.first()
                val lockMinutes = settings.autoLockMinutes.first().coerceIn(1, 60)
                if (enabled && pausedAtMs > 0) {
                    val awayMs = System.currentTimeMillis() - pausedAtMs
                    if (awayMs > lockMinutes * 60_000L) {
                        requestBiometricSuspend()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pausedAtMs = System.currentTimeMillis()
    }

    override fun onDestroy() {
        super.onDestroy()
        MainActivityHolder.detach()
    }

    /** 生物识别验证（挂起直到结果）。 */
    suspend fun requestBiometricSuspend(): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            biometricContinuation = cont
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        cont.resume(false)
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁 郑财校园")
                .setSubtitle("使用指纹或面容验证身份")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build()
            prompt.authenticate(info)
        }
}

/** Compose 层访问 Activity 的桥。 */
object MainActivityHolder {
    private var activity: MainActivity? = null

    fun attach(a: MainActivity) {
        activity = a
    }

    fun detach() {
        activity = null
    }

    suspend fun requestBiometric(): Boolean = activity?.requestBiometricSuspend() ?: true
}
