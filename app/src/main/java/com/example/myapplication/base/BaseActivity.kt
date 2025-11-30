package com.example.myapplication.base

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewbinding.ViewBinding
import com.airbnb.lottie.animation.content.Content
import com.example.myapplication.ui.no_internet.NoInternetActivity
import com.example.myapplication.utils.CheckInternet

abstract class BaseActivity<VB : ViewBinding>(
    val inflater: (LayoutInflater) -> VB
) : AppCompatActivity() {

    protected val binding: VB by lazy { inflater(layoutInflater) }
    protected open val belowSystemBars = true

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        enableEdgeToEdge()

        if (belowSystemBars) setupPaddingSystemBars()

        applyHideSystemBars()
        setupInternet()

        initView()
        bindView()
        bindViewModel()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyHideSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    // IMMERSIVE
    private fun applyHideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, binding.root).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    // INSETS (padding bottom dengan keyboard/nav bar)
    private fun setupPaddingSystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                nav.bottom + ime.bottom
            )
            insets
        }
    }

    // INTERNET
    private fun setupInternet() {
        connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val realInternet =
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                runOnUiThread {
                    if (realInternet) {
                        if (this@BaseActivity is NoInternetActivity) finish()
                    } else {
                        if (this@BaseActivity !is NoInternetActivity) {
                            startActivity(
                                Intent(
                                    this@BaseActivity,
                                    NoInternetActivity::class.java
                                )
                            )
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    if (this@BaseActivity !is NoInternetActivity) {
                        startActivity(
                            Intent(
                                this@BaseActivity,
                                NoInternetActivity::class.java
                            )
                        )
                    }
                }
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    open fun initView() {}
    open fun bindView() {}
    open fun bindViewModel() {}
}
