package br.edu.ifsc.garopaba.exploregaropabahelper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.maps.android.data.kml.KmlLayer
import com.google.maps.android.data.kml.KmlLineString
import java.io.File

class MapViewActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private lateinit var map: GoogleMap
    private lateinit var kmlLayer: KmlLayer
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var btnFocusRoute: FloatingActionButton
    private lateinit var btnFocusLocation: FloatingActionButton
    private lateinit var btnZoomIn: FloatingActionButton
    private lateinit var btnZoomOut: FloatingActionButton
    private var isFocusingOnUserLocation: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_view)

        btnFocusRoute = findViewById(R.id.btnFocusRoute)
        btnFocusLocation = findViewById(R.id.btnFocusLocation)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val kmlFileName = intent.getStringExtra("KML_FILE_NAME")
        supportActionBar?.title = kmlFileName

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        btnFocusRoute.setOnClickListener {
            focusOnKmlLayer()
            isFocusingOnUserLocation = false
        }

        btnFocusLocation.setOnClickListener {
            isFocusingOnUserLocation = true
            focusOnUserLocation()
        }

        btnZoomIn.setOnClickListener {
            val currentZoom = map.cameraPosition.zoom
            map.animateCamera(CameraUpdateFactory.zoomTo(currentZoom + 1))
        }

        btnZoomOut.setOnClickListener {
            val currentZoom = map.cameraPosition.zoom
            map.animateCamera(CameraUpdateFactory.zoomTo(currentZoom - 1))
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.mapType = GoogleMap.MAP_TYPE_SATELLITE
        map.uiSettings.isMyLocationButtonEnabled = false

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }

        val kmlFilePath = intent.getStringExtra("KML_FILE_PATH") ?: return
        try {
            val kmlFile = File(kmlFilePath)
            val inputStream = kmlFile.inputStream()

            kmlLayer = KmlLayer(map, inputStream, applicationContext)
            kmlLayer.addLayerToMap()

            setKmlLayerPolylineColor(kmlLayer)
            focusOnKmlLayer()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao carregar o arquivo KML", Toast.LENGTH_LONG).show()
        }
    }

    private fun focusOnKmlLayer() {
        val boundsBuilder = LatLngBounds.Builder()
        for (container in kmlLayer.containers) {
            for (placemark in container.placemarks) {
                val geometry = placemark.geometry
                if (geometry is KmlLineString) {
                    for (latLng in geometry.geometryObject) {
                        boundsBuilder.include(latLng)
                    }
                }
            }
        }

        val bounds = boundsBuilder.build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    private fun focusOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentZoom = map.cameraPosition.zoom
                    val currentLocation = LatLng(location.latitude, location.longitude)
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            currentLocation,
                            currentZoom
                        )
                    )
                } else {
                    Toast.makeText(
                        this,
                        "Não foi possível obter a localização atual",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(5000)
            .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
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
            if (isFocusingOnUserLocation) {
                for (location in locationResult.locations) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                }
            }
        }
    }

    private fun setKmlLayerPolylineColor(kmlLayer: KmlLayer) {
        for (container in kmlLayer.containers) {
            for (placemark in container.placemarks) {
                val geometry = placemark.geometry
                if (geometry is KmlLineString) {
                    val polylineOptions = PolylineOptions()
                        .color(android.graphics.Color.RED)
                        .width(5f)
                        .addAll(geometry.geometryObject)

                    map.addPolyline(polylineOptions)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    map.isMyLocationEnabled = true
                    startLocationUpdates()
                }
            } else {
                Toast.makeText(
                    this,
                    "Permissão de localização necessária",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

}
