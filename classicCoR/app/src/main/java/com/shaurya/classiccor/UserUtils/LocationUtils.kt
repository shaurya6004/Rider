package com.shaurya.classiccor.UserUtils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.text.TextUtils
import java.io.IOException
import java.lang.StringBuilder
import java.util.*

object LocationUtils {
    fun getAddressFromLocation(context: Context?, location: Location):String{
        val result = StringBuilder()
        val geocoder = Geocoder(context, Locale.getDefault())
        val addressList:List<Address>?
        return try {
            addressList = geocoder.getFromLocation(location.latitude!!,location.longitude!!,1)
            if (addressList != null && addressList.size > 0)
            {
                if (addressList[0].locality != null && !TextUtils.isEmpty(addressList[0].locality))
                {
                    //If address have city field
                    result.append(addressList[0].locality)

                }
                else  if(addressList[0].subAdminArea != null && !TextUtils.isEmpty(addressList[0].subAdminArea))

                //if don't have city field, then we look for subadminarea
                    result.append(addressList[0].subAdminArea)

                else  if(addressList[0].adminArea != null && !TextUtils.isEmpty(addressList[0].adminArea))
                {

                    // if don't have subadminarea , then we look for adminarea
                    result.append(addressList[0].adminArea)

                } else
                {
                    // if we don't have admin area then we look for country Name
                    result.append(addressList[0].countryName)
                }
                //Final result, apply country code
                result.append(addressList[0].countryCode)
            }
            result.toString()
        }catch (e: IOException){
            result.toString()
        }
    }
}