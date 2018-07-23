package free.mtutunik.mygooglemap

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.media.Image
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.widget.ImageButton
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*
import kotlin.concurrent.fixedRateTimer

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private val TAG = "MapsActivity"
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_CHECK_SETTINGS = 2
        private const val MAP_DEFAULT_ZOOM = 15f
        private const val POSITION_FILE_NAME = "testposition.txt"
        private const val AUDIO_FNAME = "voice"
        private const val AUDIO_RECORD_PERMISSION_REQUEST_CODE = 2

    }

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationCallback: LocationCallback
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mLastLocation: Location
    private var mLocationUpdateState = false
    private var mPolyOptions = PolylineOptions()
    private lateinit var mLocationOutStream : FileOutputStream
    private lateinit var mLocationFile : File
    private val hasMic : Boolean
        get() {
            val pmanager = this.packageManager
            return pmanager.hasSystemFeature(
                    PackageManager.FEATURE_MICROPHONE)
        }

    private var mMediaRecorder: MediaRecorder? = null
    private var mMediaPlayer: MediaPlayer? = null
    private var mIsRecording = false

    private var mPermissionToRecordAccepted = false;
    private var permissions = { Manifest.permission.RECORD_AUDIO};

    private lateinit var mMicButton : ImageButton
    private lateinit var mStopButton : ImageButton
    private lateinit var mPlayButton : ImageButton
    private lateinit var mNewTourButton: ImageButton

    private lateinit var mDbHelper: DbHelper
    var mPlayerService: PlayerService? = null
    var mIsBound = false

    private val mPlayerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName,
                                        service: IBinder) {
            val binder = service as PlayerService.PlayerBinder
            mPlayerService = binder.getService()
            mPlayerService?.mDbHelper = mDbHelper
            mIsBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mIsBound = false
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)

        mDbHelper = DbHelper(this)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val filename = filesDir.absolutePath + File.separator + POSITION_FILE_NAME
        mLocationOutStream = openFileOutput(POSITION_FILE_NAME, Context.MODE_PRIVATE)

        mMicButton = findViewById(R.id.mic_button) as ImageButton
        mStopButton = findViewById(R.id.stop_button) as ImageButton
        mPlayButton = findViewById(R.id.play_button) as ImageButton
        mNewTourButton = findViewById(R.id.new_tour_button) as ImageButton

        mPermissionToRecordAccepted = checkRecordPermission()

        mMicButton.setOnClickListener() {

            val time = System.currentTimeMillis()
            val fname = filesDir.absolutePath + File.separator + "mapaudio-${time}.aac"
            recordAudio(fname)
        }

        mStopButton.setOnClickListener() {
            if (mIsRecording) {
                mPlayerService?.stopRecordingAudio()
            }
            else {
                mPlayerService?.stopPlayingAudio()
            }
        }

        mNewTourButton.setOnClickListener() {
            createNewTour()
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)



        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                mLastLocation = p0.lastLocation
                mPlayerService.mLastLocation = mLastLocation

                updateTracking(p0.lastLocation)
            }
        }

        var intent = Intent(this, PlayerService::class.java)

        bindService(intent, mPlayerServiceConnection, Context.BIND_AUTO_CREATE)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                mLocationUpdateState = true
                //startLocationUpdates()
            }
        }
    }


    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
        unbindService(mPlayerServiceConnection)
        super.onDestroy()
    }

    public override fun onResume() {
        super.onResume()
        if (!mLocationUpdateState) {
            //startLocationUpdates()
        }

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        setupMap()
        //showRoute(googleMap)
    }

    fun setupMap() {

        if (!checkGeoPermission()) {
            return
        }


        mMap.isMyLocationEnabled = true



        mFusedLocationClient.lastLocation.addOnSuccessListener(this){ location ->
            mLastLocation = location
            mPlayerService.mLastLocation = mLastLocation
            moveCameraToLocation(location)
            createLocationRequest()
            startLocationUpdates()
        }


    }


    fun moveCameraToLocation(location: Location?) {
        if (location == null || mMap == null) {
            return
        }

        val newPoint = LatLng(location.latitude, location.longitude)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPoint, MAP_DEFAULT_ZOOM))
    }


    fun showRoute(googleMap: GoogleMap) {
        mMap = googleMap
        // declare bounds object to fit whole route in screen
        val LatLongB = LatLngBounds.Builder()

        // Add markers
        val sydney = LatLng(-34.0, 151.0)
        val opera = LatLng(-33.9320447,151.1597271)
        mMap!!.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap!!.addMarker(MarkerOptions().position(opera).title("Opera House"))

        // Declare polyline object and set up color and width
        val options = PolylineOptions()
        options.color(Color.RED)
        options.width(5f)

        // build URL to call API
        val url = getURL(sydney, opera)

        doAsync(task = {
            // Connect to URL, download content and convert into string asynchronously
            val result = URL(url).readText()
            uiThread({ it: MapsActivity ->
                /*
                // When API call is done, create parser and convert into JsonObjec

                val parser: Parser = Parser()
                val stringBuilder: StringBuilder = StringBuilder(result)
                val json: JsonObject = parser.parse(stringBuilder) as JsonObject
                // get to the correct element in JsonObject
                val routes = json.array<JsonObject>("routes")
                val points = routes!!["legs"]["steps"][0] as JsonArray<JsonObject>
                // For every element in the JsonArray, decode the polyline string and pass all points to a List
                val polypts = points.flatMap { decodePoly(it.obj("polyline")?.string("points")!!) }
                // Add  points to polyline and bounds
                options.add(sydney)
                LatLongB.include(sydney)
                for (point in polypts) {
                    options.add(point)
                    LatLongB.include(point)
                }
                options.add(opera)
                LatLongB.include(opera)
                // build bounds
                val bounds = LatLongB.build()
                // add polyline to the map
                mMap!!.addPolyline(options)
                // show map with route centered
                mMap!!.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                */
                mMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(sydney, 16f))
            })
        })
    }

    private fun getURL(from : LatLng, to : LatLng) : String {
        val origin = "origin=" + from.latitude + "," + from.longitude
        val dest = "destination=" + to.latitude + "," + to.longitude
        val sensor = "sensor=false"
        val params = "$origin&$dest&$sensor"
        return "https://maps.googleapis.com/maps/api/directions/json?$params"
    }

    /**
     * Method to decode polyline points
     * Courtesy : https://jeffreysambells.com/2010/05/27/decoding-polylines-from-google-maps-direction-api-with-java
     */
    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5,
                    lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }


    private fun checkGeoPermission() : Boolean {
        if (ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE)
            return false
        }

        return true
    }

    private fun checkRecordPermission() : Boolean {
        if (ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    AUDIO_RECORD_PERMISSION_REQUEST_CODE)
            return false
        }

        return true
    }

    private fun startLocationUpdates() {

        if (!checkGeoPermission()) {
            return
        }

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null /* Looper */)
    }

    private fun createLocationRequest() {

        mLocationRequest = LocationRequest()
        mLocationRequest.interval = 1000
        mLocationRequest.fastestInterval = 500
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest)

        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            mLocationUpdateState = true
            startLocationUpdates()
        }
        task.addOnFailureListener { e ->
            // 6
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    e.startResolutionForResult(this@MapsActivity,
                            REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }


    private fun updatePolyOptions(location: Location) {
        mPolyOptions.add(LatLng(location.latitude, location.longitude)).width(8f).color(Color.GREEN)
    }

    private fun updateTracking(location: Location) {
        updatePolyOptions(location)
        mMap!!.addPolyline(mPolyOptions)
        moveCameraToLocation(location)

        saveCurrentLocation(location)
    }

    private fun saveCurrentLocation(location: Location) {

        val line = "${location.latitude}, ${location.longitude}, ${location.time}\n"
        mLocationOutStream.writer().append(line).flush()

    }

    private fun recordAudio(fname: String) {
        if (!mPermissionToRecordAccepted) {
            Log.d(TAG, "Recording not allowed!")
            return
        }

        mPlayerService?.recordAudio(fname)
        mIsRecording = true

    }

    fun stopRecordingAudio() {
        mPlayerService?.stopRecordingAudio()
    }

    fun stopPlayingAudio() {
        mPlayerService?.stopPlayingAudio()
    }


    fun playAudio(fname : String) {
        mPlayerService?.playAudio(fname)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_RECORD_PERMISSION_REQUEST_CODE) {
            mPermissionToRecordAccepted = (grantResults[0] == PackageManager.PERMISSION_GRANTED)

        }
    }


    fun createNewTour() {
        mDbHelper.createNewTour("tour")
    }
}
