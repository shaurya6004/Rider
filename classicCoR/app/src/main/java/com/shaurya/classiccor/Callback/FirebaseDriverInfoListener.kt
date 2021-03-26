package com.shaurya.classiccor.Callback

import com.shaurya.classiccor.Model.DriverGeoModel

interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}