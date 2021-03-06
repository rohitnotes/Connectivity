package com.gappscorp.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

internal class ConnectivityLiveData(private val defaultValue: Boolean) : LiveData<Boolean>(defaultValue) {

    private lateinit var connectivityManager: ConnectivityManager

    private val callbacks by lazy { mutableListOf<ConnectionListener>() }
    private val localObserver by lazy { LocalObserver(defaultValue) }

    fun addConnectionListener(listener: ConnectionListener) {
        if (!callbacks.contains(listener)) callbacks.add(listener)
        if (callbacks.isNotEmpty()) {
            localObserver.registerObserver { event ->
                callbacks.forEach { callback ->
                    callback.onConnectivityChanged(event)
                }
            }
        }
        // if network was not available while registering callback,
        // notify newly attached listener
        if (!value!!)
            listener.onConnectivityChanged(false)
    }

    fun removeConnectionListener(listener: ConnectionListener) {
        if (callbacks.contains(listener)) callbacks.remove(listener)
        if (callbacks.isEmpty()) {
            localObserver.unregisterObserver()
        }
    }

    fun bind(owner: LifecycleOwner) {
        observe(owner, localObserver)
    }

    fun unBind() {
        removeObserver(localObserver)
    }

    override fun observe(owner: LifecycleOwner, observer: Observer<in Boolean>) {
        if (owner is Context && !::connectivityManager.isInitialized) {
            connectivityManager =
                    owner.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }
        super.observe(owner, observer)
    }

    override fun onActive() {
        connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build(),
                networkCallback
        )
    }

    override fun onInactive() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private val networkCallback = object :
            ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            postValue(true)
        }

        override fun onUnavailable() {
            postValue(false)
        }

        override fun onLost(network: Network) {
            postValue(false)
        }
    }
}

private class LocalObserver(defaultValue: Boolean) : Observer<Boolean> {
    private var oldValue: Boolean = defaultValue
    private var stateChanged: Boolean = false

    private var localCallback: ((value: Boolean) -> Unit)? = null

    override fun onChanged(value: Boolean?) {
        value?.let {
            stateChanged = oldValue != it
            oldValue = it
            if (stateChanged)
                localCallback?.invoke(oldValue)
        }
    }

    fun registerObserver(callback: ((value: Boolean) -> Unit)? = null) {
        localCallback = callback
    }

    fun unregisterObserver() {
        localCallback = null
    }
}