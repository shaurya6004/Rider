package com.shaurya.classiccor.Remote

import com.shaurya.classiccor.Model.FCMResponse
import com.shaurya.classiccor.Model.FCMSendData
import io.reactivex.Observable
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.*

interface IFCMService {
    @Headers(
        "Content-Type:application/json",
        "Authorization:key=AAAAxYi7Hws:APA91bEDpyW52MNd6mMzYcf-4UTXhEhrZN1OH-EoZewJr0gIgR0XvkH77L5JVazNp1Ad2B9UNrQx-YSClp2644XjOioDiaWgjSwWBYJTsB0pJzmnl_q5obnbopBXTs6yp6h2DUbpvf-K"
    )
    @POST("fcm/send")
    fun sendNotification(@Body body: FCMSendData?):Observable<FCMResponse?>?
}