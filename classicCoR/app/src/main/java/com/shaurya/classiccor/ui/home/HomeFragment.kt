package com.shaurya.classiccor.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.shaurya.classiccor.Callback.FirebaseDriverInfoListener
import com.shaurya.classiccor.Callback.FirebaseFailedListener
import com.shaurya.classiccor.Common.Common
import com.shaurya.classiccor.Model.AnimationModel
import com.shaurya.classiccor.Model.DriverGeoModel
import com.shaurya.classiccor.Model.DriverInfoModel
import com.shaurya.classiccor.Model.EventBus.SelectedPlaceEvent
import com.shaurya.classiccor.Model.GeoQueryModel
import com.shaurya.classiccor.R
import com.shaurya.classiccor.Remote.IGoogleAPI
import com.shaurya.classiccor.Remote.RetrofitClient
import com.shaurya.classiccor.RequestDriverActivity
import com.shaurya.classiccor.UserUtils.LocationUtils
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.Observable

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject

import java.io.IOException
import java.util.*


class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {

    private var isNextLaunch: Boolean=false
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    private lateinit var slidingUpPanelLayout: SlidingUpPanelLayout
    private lateinit var txt_welcome:TextView
    private lateinit var autoCompleteSupportFragment: AutocompleteSupportFragment

    //Location
     var locationRequest: LocationRequest? = null
     var locationCallback: LocationCallback? =null
     var fusedLocationProviderClient: FusedLocationProviderClient? = null

    //Load Driver
    var distance = 1.0
    val LIMIT_RANGE = 10.0
    var previousLocation: Location? = null
    var currentLocation: Location? = null
    var firstTime = true


    //Listener
    lateinit var iFirebaseDriverInfoListener: FirebaseDriverInfoListener
    lateinit var iFirebaseFailedListener: FirebaseFailedListener

    var cityName = ""

    //
    val compositeDisposable = CompositeDisposable()
    lateinit var iGoogleAPI: IGoogleAPI

    override fun onResume() {
        super.onResume()
        if (isNextLaunch)
            loadAvailableDrivers()
        else
            isNextLaunch = true
    }



    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()
    }


    override fun onDestroy() {
        fusedLocationProviderClient!!.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)





        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


//        initViews(root)
        init()
        return root
    }

//    private fun initViews(root: View?) {
//        slidingUpPanelLayout = root!!.findViewById(R.id.activity_main) as SlidingUpPanelLayout
//        txt_welcome = root!!.findViewById(R.id.txt_welcome) as TextView
//
//        Common.setWelcomeMessage(txt_welcome)
//    }

    private fun init() {

        Places.initialize(requireContext(),getString(R.string.google_maps_key))
        autoCompleteSupportFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autoCompleteSupportFragment.setPlaceFields(Arrays.asList(Place.Field.ID,
        Place.Field.ADDRESS,
        Place.Field.LAT_LNG,
        Place.Field.NAME))
        autoCompleteSupportFragment.setOnPlaceSelectedListener(object:PlaceSelectionListener{
            override fun onPlaceSelected(p0: Place) {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                )  {
                    Snackbar.make(requireView(),getString(R.string.permisssion_require),Snackbar.LENGTH_LONG).show()
                    return
                }


                fusedLocationProviderClient!!
                    .lastLocation.addOnSuccessListener { location ->
                        val origin = LatLng(location.latitude, location.longitude)
                        val destination = LatLng(p0.latLng!!.latitude,p0.latLng!!.longitude)

                        startActivity(Intent(requireContext(), RequestDriverActivity::class.java))
                        EventBus.getDefault().postSticky(SelectedPlaceEvent(origin,destination,p0!!.address!!))
                    }

            }

            override fun onError(p0: Status) {
               Snackbar.make(requireView(),""+p0.statusMessage!!,Snackbar.LENGTH_LONG).show()
            }
        })

        iGoogleAPI = RetrofitClient.instance!!.create(IGoogleAPI::class.java)

        iFirebaseDriverInfoListener = this

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        )  {
            Snackbar.make(mapFragment.requireView(),getString(R.string.permisssion_require),Snackbar.LENGTH_LONG).show()
            return
        }

        buildLocationRequest()
        buildLocationCallback()
        updateLocation()




        loadAvailableDrivers()
    }

    private fun updateLocation() {
        if (fusedLocationProviderClient == null) {
            fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(requireContext())
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                return
            }
            fusedLocationProviderClient!!.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.myLooper()
            )
        }
    }

    private fun buildLocationCallback() {
        if (locationCallback == null)
        {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(p0: LocationResult?) {
                    super.onLocationResult(p0)
                    val newPos = LatLng(p0!!.lastLocation.latitude, p0!!.lastLocation.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                    //If user had change the Location, calculate and Load driver again
                    if (firstTime) {
                        previousLocation = p0.lastLocation
                        currentLocation = p0.lastLocation



                        firstTime = false

                    } else {
                        previousLocation = currentLocation
                        currentLocation = p0.lastLocation
                    }

                    setRestrictPlacesIncountry(p0!!.lastLocation)

                    if (previousLocation!!.distanceTo(currentLocation) / 1000 <= LIMIT_RANGE)
                        loadAvailableDrivers()
                }
            }

        }
    }

    private fun buildLocationRequest() {
        if (locationRequest == null)
        {
            locationRequest = LocationRequest()
            locationRequest!!.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            locationRequest!!.setFastestInterval(3000)
            locationRequest!!.setSmallestDisplacement(10f)
            locationRequest!!.interval = 5000
        }
    }

    private fun setRestrictPlacesIncountry(location: Location?) {
        try {
            val geoCoder = Geocoder(requireContext(), Locale.getDefault())
            var addressList= geoCoder.getFromLocation(location!!.latitude, location.longitude, 1)
            if (addressList.size >0)
                autoCompleteSupportFragment.setCountry(addressList[0].countryCode)
        }catch (e:IOException) {
            e.printStackTrace()
        }

    }

    private fun loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
//            Snackbar.make(requireView(),getString(R.string.permisssion_require),Snackbar.LENGTH_SHORT).show()


            return
        }
        fusedLocationProviderClient!!.lastLocation
            .addOnFailureListener { e ->
                Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
            }
            .addOnSuccessListener { location ->

                cityName = LocationUtils.getAddressFromLocation(requireContext(), location)
                if (!TextUtils.isEmpty(cityName)) {
                    if (!TextUtils.isEmpty(cityName)) {
                        val driver_location_ref = FirebaseDatabase.getInstance()
                            .getReference(Common.DRIVERS_LOCATION_REFRENCES)
                            .child(cityName)
                        val gf = GeoFire(driver_location_ref)
                        val geoQuery = gf.queryAtLocation(
                            GeoLocation(location.latitude, location.longitude),
                            distance
                        )
                        geoQuery.removeAllListeners()

                        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                            override fun onGeoQueryReady() {
                                if (distance <= LIMIT_RANGE) {
                                    distance++
                                    loadAvailableDrivers()
                                } else {
                                    distance = 0.0
                                    addDriverMarker()
                                }
                            }

                            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                                //Common.driversFound.add(DriverGeoModel(key!!, location!!))
                                if (!Common.driversFound.containsKey(key))
                                    Common.driversFound[key!!] = DriverGeoModel(key, location)
                            }

                            override fun onKeyMoved(key: String?, location: GeoLocation?) {

                            }

                            override fun onKeyExited(key: String?) {

                            }

                            override fun onGeoQueryError(error: DatabaseError?) {
                                Snackbar.make(requireView(), error!!.message, Snackbar.LENGTH_SHORT)
                                    .show()

                            }

                        })

                        driver_location_ref.addChildEventListener(object : ChildEventListener {
                            override fun onCancelled(p0: DatabaseError) {
                                Snackbar.make(requireView(), p0.message, Snackbar.LENGTH_SHORT)
                                    .show()
                            }

                            override fun onChildMoved(
                                p0: DataSnapshot, p1: String?
                            ) {

                            }

                            override fun onChildChanged(
                                p0: DataSnapshot, p1: String?
                            ) {

                            }

                            override fun onChildAdded(
                                p0: DataSnapshot, p1: String?
                            ) {
                                //Have new driver
                                val geoQueryModel = p0.getValue(GeoQueryModel::class.java)
                                val geoLocation =
                                    GeoLocation(geoQueryModel!!.l!![0], geoQueryModel!!.l!![1])
                                val driverGeoModel = DriverGeoModel(p0.key, geoLocation)
                                val newDriverLocation = Location("")
                                newDriverLocation.latitude = geoLocation.latitude
                                newDriverLocation.longitude = geoLocation.longitude
                                val newDistance =
                                    location.distanceTo(newDriverLocation) / 1000 //in km
                                if (newDistance <= LIMIT_RANGE)
                                    findDriverByKey(driverGeoModel)
                            }

                            override fun onChildRemoved(p0: DataSnapshot) {

                            }

                        })
                    } else
                        Snackbar.make(
                            requireView(),
                            getString(R.string.city_name_not_found),
                            Snackbar.LENGTH_LONG
                        ).show()


                }
            }




    }

    private fun addDriverMarker() {
        if (Common.driversFound.size > 0) {
            Observable.fromIterable(Common.driversFound.keys)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { key: String? ->
                        findDriverByKey(Common.driversFound[key!!])
                    },
                    { t: Throwable ->
                        Snackbar.make(requireView(), t!!.message!!, Snackbar.LENGTH_SHORT).show()

                    }
                )

        } else {
            Snackbar.make(
                requireView(),
                getString(R.string.drivers_not_found),
                Snackbar.LENGTH_SHORT
            ).show()


        }

    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel?) {
        FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_INFO_REFERENCE)
            .child(driverGeoModel!!.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(p0.message)
                }

                override fun onDataChange(p0: DataSnapshot) {
                    if (p0.hasChildren()) {
                        driverGeoModel.driverInfoModel = (p0.getValue(DriverInfoModel::class.java))
                        Common.driversFound[driverGeoModel.key!!]!!.driverInfoModel = (p0.getValue(DriverInfoModel::class.java))
                        iFirebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel)
                    } else
                        iFirebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found) + driverGeoModel.key)

                }

            })

    }


    override fun onMapReady(p0: GoogleMap?) {
        mMap = p0!!

        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {

                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                        return
                    }
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener {
                        fusedLocationProviderClient!!.lastLocation
                            .addOnFailureListener { e ->
                                Snackbar.make(
                                    requireView(), e.message!!,
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                            .addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                mMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        userLatLng,
                                        18f
                                    )
                                )
                            }


                        true

                    }
                    //Layout button
                    val locationButton = (mapFragment.requireView()!!
                        .findViewById<View>("1".toInt())!!.parent!! as View)
                        .findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250   // Move to see zoom control

                    //Update Location
                    buildLocationRequest()
                    buildLocationCallback()
                    updateLocation()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {

                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Snackbar.make(
                        requireView(), p0!!.permissionName + "needed to run the app",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            })
            .check()
        //Enable Zoom
        mMap.uiSettings.isZoomControlsEnabled = true



        try {
            val success = p0!!.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.drive_co
                )
            )
            if (!success)
                Snackbar.make(
                    requireView(), "Load map style failed",
                    Snackbar.LENGTH_LONG
                ).show()
        } catch (e: Exception) {
            Snackbar.make(
                requireView(), "" + e.message!!,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        //if already have marker with this key, does'nt see it again
        if (!Common.markerList.containsKey(driverGeoModel!!.key!!))
            Common.markerList.put(driverGeoModel!!.key!!,
                mMap.addMarker(
                    MarkerOptions().position(
                            LatLng(
                                driverGeoModel!!.geoLocation!!.latitude,
                                driverGeoModel!!.geoLocation!!.longitude
                            )
                        )
                        .flat(true)
                        .title(
                            Common.buildNames(
                                driverGeoModel.driverInfoModel!!.firstName,
                                driverGeoModel.driverInfoModel!!.lastName
                            )
                        )
                        .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.driver_icon_27013))
                )
            )
        if (!TextUtils.isEmpty(cityName)) {
            val driverLocation = FirebaseDatabase.getInstance()
                .getReference(Common.DRIVERS_LOCATION_REFRENCES)
                .child(cityName)
                .child(driverGeoModel!!.key!!)
            driverLocation.addValueEventListener(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    Snackbar.make(requireView(), p0.message, Snackbar.LENGTH_SHORT).show()
                }

                override fun onDataChange(p0: DataSnapshot) {
                    if (!p0.hasChildren()) {
                        if (Common.markerList.get(driverGeoModel!!.key!!) != null) {
                            val marker = Common.markerList.get(driverGeoModel!!.key!!)
                            marker!!.remove() // Remove marker from map
                            Common.markerList.remove(driverGeoModel!!.key!!) // Remove marker Information
                            Common.driversSubscribe.remove(driverGeoModel.key!!) //Remove driver Information

                            //When driver decline request they can accept again if they stop and open app again
                            if (Common.driversFound != null && Common.driversFound[driverGeoModel!!.key] != null)
                                Common.driversFound.remove(driverGeoModel!!.key!!)
                            driverLocation.removeEventListener(this)
                        }
                    } else {
                        if (Common.markerList.get(driverGeoModel!!.key!!) != null) {
                            val geoQueryModel = p0!!.getValue(GeoQueryModel::class.java)
                            val animationModel = AnimationModel(false, geoQueryModel!!)
                            if (Common.driversSubscribe.get(driverGeoModel.key!!) != null) {
                                val marker = Common.markerList.get(driverGeoModel.key!!)
                                val oldPosition = Common.driversSubscribe.get(driverGeoModel.key!!)

                                val from = StringBuilder()
                                    .append(oldPosition!!.geoQueryModel!!.l?.get(0))
                                    .append(",")
                                    .append(oldPosition!!.geoQueryModel!!.l?.get(1))
                                    .toString()

                                val to = StringBuilder()
                                    .append(animationModel.geoQueryModel!!.l?.get(0))
                                    .append(",")
                                    .append(animationModel.geoQueryModel!!.l?.get(1))
                                    .toString()

                                moveMarkerAnimation(
                                    driverGeoModel.key!!,
                                    animationModel,
                                    marker,
                                    from,
                                    to
                                )

                            } else
                                Common.driversSubscribe.put(
                                    driverGeoModel.key!!,
                                    animationModel
                                ) //First location init
                        }
                    }
                }
            })
        }

    }

    private fun moveMarkerAnimation(
        key: String,
        newData: AnimationModel,
        marker: Marker?,
        from: String,
        to: String
    ) {
        if (!newData.isRun) {
            //Request API
            compositeDisposable.add(iGoogleAPI.getDirections(
                "driving", "less_driving",
                from, to,
                getString(R.string.google_api_key)
            )
            !!.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { returnResult ->
                    Log.d("API_RETURN", returnResult)
                    try {
                        val jsonObject = JSONObject(returnResult)
                        val jsonArray = jsonObject.getJSONArray("routes")
                        for (i in 0 until jsonArray.length()) {
                            val route = jsonArray.getJSONObject(i)
                            val poly = route.getJSONObject("overview_polyline")
                            val polyline = poly.getString("points")
//                            polyLineList = Common.decodePoly(polyline)
                            newData.polyLineList = Common.decodePoly(polyline)
                        }

                        //Moving
                        newData.index = 1
                        newData.next = 1

                        val runnable = object : Runnable {
                            override fun run() {
                                if (newData.polyLineList != null && newData.polyLineList!!.size > 1) {
                                    if (newData.index < newData.polyLineList!!.size - 2) {
                                        newData.index++
                                        newData.next = newData.index + 1
                                        newData.start = newData.polyLineList!![newData.index]
                                        newData.end = newData.polyLineList!![newData.next]
                                    }
                                    val valueAnimator = ValueAnimator.ofInt(0, 1)
                                    valueAnimator.duration = 3000
                                    valueAnimator.interpolator = LinearInterpolator()
                                    valueAnimator.addUpdateListener { value ->
                                        newData.v = value.animatedFraction
                                        newData.lat = newData.v * newData.end!!.latitude + (1 - newData.v) * newData.start!!.latitude
                                        newData.lng = newData.v * newData.end!!.longitude + (1 - newData.v) * newData.start!!.longitude
                                        val newPos = LatLng(newData.lat, newData.lng)
                                        marker!!.position = newPos
                                        marker!!.setAnchor(0.5f, 0.5f)
                                        marker!!.rotation = Common.getBearing(newData.start!!, newPos)
                                    }
                                    valueAnimator.start()
                                    if (newData.index < newData.polyLineList!!.size - 2)
                                        newData.handler!!.postDelayed(this, 1500)
                                    else if (newData.index < newData.polyLineList!!.size - 1) {
                                        newData.isRun = false
                                        Common.driversSubscribe.put(key, newData) // update
                                    }
                                }
                            }
                        }

                        newData.handler!!.postDelayed(runnable, 1500)
                    } catch (e: java.lang.Exception) {
                        Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_LONG).show()
                    }
                })

        }
    }
}