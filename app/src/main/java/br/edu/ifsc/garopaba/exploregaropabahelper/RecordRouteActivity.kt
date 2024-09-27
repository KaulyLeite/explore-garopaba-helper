package br.edu.ifsc.garopaba.exploregaropabahelper

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordRouteActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var polyline: Polyline
    private lateinit var timeTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var routeNameEditText: EditText
    private lateinit var timer: CountDownTimer
    private lateinit var btnZoomIn: FloatingActionButton
    private lateinit var btnZoomOut: FloatingActionButton
    private lateinit var btnStartRoute: Button
    private lateinit var btnEndRoute: Button
    private var isTracking = false
    private var startTime: Long = 0
    private var totalDistance: Float = 0f
    private val routePoints = mutableListOf<LatLng>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_route)

        timeTextView = findViewById(R.id.tvTime)
        distanceTextView = findViewById(R.id.tvDistance)
        routeNameEditText = findViewById(R.id.etRouteName)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        btnStartRoute = findViewById(R.id.btnStartRoute)
        btnEndRoute = findViewById(R.id.btnEndRoute)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        btnZoomIn.setOnClickListener {
            val currentZoom = map.cameraPosition.zoom
            map.animateCamera(CameraUpdateFactory.zoomTo(currentZoom + 1))
        }

        btnZoomOut.setOnClickListener {
            val currentZoom = map.cameraPosition.zoom
            map.animateCamera(CameraUpdateFactory.zoomTo(currentZoom - 1))
        }

        btnStartRoute.setOnClickListener {
            val routeName = routeNameEditText.text.toString().trim()
            if (routeName.isEmpty()) {
                Toast.makeText(
                    this,
                    "Insira um nome para o trajeto",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            isTracking = true
            routePoints.clear()
            polyline.points = routePoints
            startTime = System.currentTimeMillis()
            totalDistance = 0f
            routeNameEditText.isEnabled = false
            btnStartRoute.visibility = View.INVISIBLE
            startTimer()

            Toast.makeText(this, "Iniciando trajeto...", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                btnStartRoute.visibility = View.GONE
                btnEndRoute.visibility = View.VISIBLE
            }, 5000)
        }

        btnEndRoute.setOnClickListener {
            if (isTracking) {
                isTracking = false
                btnEndRoute.visibility = View.GONE

                val endTime = System.currentTimeMillis()
                val elapsedTime = endTime - startTime
                timer.cancel()

                val routeName = routeNameEditText.text.toString().trim()
                saveRouteToKml(routeName, elapsedTime, totalDistance)
            } else {
                Toast.makeText(this, "Nenhum trajeto ativo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.mapType = GoogleMap.MAP_TYPE_SATELLITE
        map.uiSettings.isMyLocationButtonEnabled = false

        checkLocationPermissionAndSetupMap()

        polyline = map.addPolyline(
            PolylineOptions()
                .color(Color.RED)
                .width(5f)
        )
    }

    private fun checkLocationPermissionAndSetupMap() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true

            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLocation = LatLng(location.latitude, location.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 18f))
                }
            }

            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(5000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            if (isTracking) {
                for (location in locationResult.locations) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLng(latLng))

                    if (routePoints.isNotEmpty()) {
                        val lastPoint = routePoints.last()
                        val result = FloatArray(1)
                        Location.distanceBetween(
                            lastPoint.latitude, lastPoint.longitude,
                            latLng.latitude, latLng.longitude,
                            result
                        )
                        totalDistance += result[0]
                        updateDistanceDisplay(totalDistance)
                    }

                    routePoints.add(latLng)
                    polyline.points = routePoints
                }
            }
        }
    }

    private fun startTimer() {
        timer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsedMillis = System.currentTimeMillis() - startTime
                updateTimeDisplay(elapsedMillis)
            }

            override fun onFinish() {
                // Implementação não necessária
            }
        }
        timer.start()
    }

    @SuppressLint("DefaultLocale")
    private fun updateTimeDisplay(elapsedMillis: Long) {
        val seconds = (elapsedMillis / 1000) % 60
        val minutes = (elapsedMillis / 1000 / 60) % 60
        val hours = elapsedMillis / 1000 / 3600
        timeTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    @SuppressLint("DefaultLocale")
    private fun updateDistanceDisplay(distanceInMeters: Float) {
        val kilometers = distanceInMeters / 1000
        distanceTextView.text = if (kilometers >= 1) {
            String.format("%.2f km", kilometers)
        } else {
            String.format("%.0f m", distanceInMeters)
        }
    }

    private fun saveRouteToKml(routeName: String, elapsedTime: Long, totalDistance: Float) {
        if (routePoints.isEmpty()) {
            Toast.makeText(this, "Nenhum trajeto para salvar", Toast.LENGTH_SHORT).show()
            return
        }

        val kmlDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "KMLRoutes")
        if (!kmlDir.exists()) {
            kmlDir.mkdirs()
        }

        val kmlFileName = "${routeName.replace(" ", "_")}.kml"
        val kmlFile = File(kmlDir, kmlFileName)

        val currentDateTime = SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        try {
            val writer = FileWriter(kmlFile)
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
            writer.write("<Document>\n")
            writer.write("<name>$routeName</name>\n")
            writer.write("<description>\n")
            writer.write("Data e hora: $currentDateTime\n")
            writer.write("Tempo total: ${formatElapsedTime(elapsedTime)}\n")
            writer.write("Distância total: ${formatDistance(totalDistance)}\n")
            writer.write("</description>\n")
            writer.write("<Placemark>\n")
            writer.write("<LineString>\n")
            writer.write("<tessellate>1</tessellate>\n")
            writer.write("<coordinates>\n")

            for (point in routePoints) {
                writer.write("${point.longitude},${point.latitude},0\n")
            }

            writer.write("</coordinates>\n")
            writer.write("</LineString>\n")
            writer.write("</Placemark>\n")
            writer.write("</Document>\n")
            writer.write("</kml>\n")
            writer.flush()
            writer.close()

            Toast.makeText(this, "Trajeto salvo como $kmlFileName", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao salvar o trajeto", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatElapsedTime(elapsedTime: Long): String {
        val seconds = (elapsedTime / 1000) % 60
        val minutes = (elapsedTime / 1000 / 60) % 60
        val hours = elapsedTime / 1000 / 3600
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    @SuppressLint("DefaultLocale")
    private fun formatDistance(distanceInMeters: Float): String {
        val kilometers = distanceInMeters / 1000
        return if (kilometers >= 1) {
            String.format("%.2f km", kilometers)
        } else {
            String.format("%.0f m", distanceInMeters)
        }
    }

}
