package com.example.chittireplica.call

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Real-network hook: on Android 10+ this service can screen incoming calls once the
 * user grants the app the Call Screening role (Settings screen explains how).
 * The demo/simulated-call flow does not use this service.
 */
class ChittiScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }
}
