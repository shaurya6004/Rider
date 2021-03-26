package com.shaurya.classiccor.UserUtils

import android.content.Context
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.shaurya.classiccor.Common.Common
import com.shaurya.classiccor.Model.DriverGeoModel
import com.shaurya.classiccor.Model.EventBus.SelectedPlaceEvent
import com.shaurya.classiccor.Model.FCMResponse
import com.shaurya.classiccor.Model.FCMSendData
import com.shaurya.classiccor.Model.TokenModel
import com.shaurya.classiccor.R
import com.shaurya.classiccor.Remote.IFCMService
import com.shaurya.classiccor.Remote.RetrofitFCMClient
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.lang.StringBuilder

object UserUtils {
    fun updateUser(
        view: View?,
        updateData: Map<String, Any>
    ) {
        FirebaseDatabase.getInstance()
            .getReference(Common.RIDER_INFO_REFRENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener { e ->
                Snackbar.make(view!!, e.message!!, Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener {
                Snackbar.make(view!!, "Update information Success", Snackbar.LENGTH_LONG).show()
            }
    }

    fun updateToken(context: Context, token: String) {
        val tokenModel = TokenModel()
        tokenModel.token = token

        FirebaseDatabase.getInstance()
            .getReference(Common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel) //Token Model
            .addOnFailureListener { e -> Toast.makeText(context,e.message, Toast.LENGTH_LONG).show() }
            .addOnSuccessListener {  }

    }

    fun sendRequestToDriver(context:Context, mainLayout: RelativeLayout?, foundDriver: DriverGeoModel?, selectedPlaceEvent: SelectedPlaceEvent) {
        val compositeDisposable = CompositeDisposable()
        val ifcmService = RetrofitFCMClient.instance!!.create(IFCMService::class.java)

        //Get token
        FirebaseDatabase.getInstance()
            .getReference(Common.TOKEN_REFERENCE)
            .child(foundDriver!!.key!!)
            .addListenerForSingleValueEvent(object:ValueEventListener{
                override fun onCancelled(databaseError:DatabaseError){
                    Snackbar.make(mainLayout!!,databaseError.message,Snackbar.LENGTH_LONG).show()
                }
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists())
                    {
                        val tokenModel = dataSnapshot.getValue(TokenModel::class.java)
                        val notificationData:MutableMap<String,String> = HashMap()
                        notificationData.put(Common.NOTI_TITLE,Common.REQUEST_DRIVER_TITLE)
                        notificationData.put(Common.NOTI_BODY, "This message represent for Request Driver action")
                        notificationData.put(Common.RIDER_KEY,FirebaseAuth.getInstance().currentUser!!.uid)


                        notificationData.put(Common.PICKUP_LOCATION_STRING, selectedPlaceEvent.originString)
                        notificationData.put(Common.PICKUP_LOCATION,StringBuilder()
                            .append(selectedPlaceEvent.origin.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.origin.longitude)
                            .toString())

                        notificationData.put(Common.DESTINATION_LOCATION_STRING, selectedPlaceEvent.address)
                        notificationData.put(Common.DESTINATION_LOCATION,StringBuilder()
                            .append(selectedPlaceEvent.origin.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.origin.longitude)
                            .toString())

                        val fcmSendData = FCMSendData(tokenModel!!.token,notificationData)

                        compositeDisposable.add(ifcmService.sendNotification(fcmSendData)!!
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ FCMResponse ->
                                if (FCMResponse!!.success == 0)
                                {
                                    compositeDisposable.clear()
                                    Snackbar.make(mainLayout!!,context.getString(R.string.send_request_driver_failed),Snackbar.LENGTH_LONG).show()
                                }

                            },{t : Throwable? ->
                                compositeDisposable.clear()
                                Snackbar.make(mainLayout!!,t!!.message!!, Snackbar.LENGTH_LONG).show()
                            }))
                    }
                    else
                    {
                        Snackbar.make(mainLayout!!,context.getString(R.string.token_not_found),Snackbar.LENGTH_LONG).show()
                    }
                }


            })
    }
}
