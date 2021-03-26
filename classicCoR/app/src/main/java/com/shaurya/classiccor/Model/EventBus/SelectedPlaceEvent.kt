package com.shaurya.classiccor.Model.EventBus

import com.google.android.gms.maps.model.LatLng
import java.lang.StringBuilder

class SelectedPlaceEvent(var origin: LatLng, var destination: LatLng, var address: String) {
    val originString:String
    get() = StringBuilder()
        .append(origin.latitude)
        .append(",")
        .append(origin.longitude)
        .toString()
    val destinationString:String
    get() = StringBuilder()
        .append(destination.latitude)
        .append(",")
        .append(destination.longitude)
        .toString()
}