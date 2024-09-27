package br.edu.ifsc.garopaba.exploregaropabahelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_READ_KML = 100
        private const val REQUEST_CODE_RECORD_ROUTE = 101
    }

    private lateinit var btnReadKml: Button
    private lateinit var btnRecordRoute: Button
    private lateinit var locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnReadKml = findViewById(R.id.btnReadKml)
        btnRecordRoute = findViewById(R.id.btnRecordRoute)

        locationSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Configuração de localização ativada", Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(this, "Configuração de localização não ativada", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        btnReadKml.setOnClickListener {
            if (checkLocationPermission()) {
                checkLocationSettingsAndProceed { openKmlListActivity() }
            } else {
                requestLocationPermission(REQUEST_CODE_READ_KML)
            }
        }

        btnRecordRoute.setOnClickListener {
            if (checkLocationPermission()) {
                checkLocationSettingsAndProceed { openRecordRouteActivity() }
            } else {
                requestLocationPermission(REQUEST_CODE_RECORD_ROUTE)
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission(requestCode: Int) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            requestCode
        )
    }

    private fun checkLocationSettingsAndProceed(action: () -> Unit) {
        val locationRequest = LocationRequest.Builder(5000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            action()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: Exception) {
                    Toast.makeText(
                        this,
                        "Erro ao solicitar ativação da localização",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Configurações de localização não disponíveis",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openKmlListActivity() {
        val intent = Intent(this, KmlListActivity::class.java)
        startActivity(intent)
    }

    private fun openRecordRouteActivity() {
        val intent = Intent(this, RecordRouteActivity::class.java)
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                REQUEST_CODE_READ_KML -> checkLocationSettingsAndProceed { openKmlListActivity() }
                REQUEST_CODE_RECORD_ROUTE -> checkLocationSettingsAndProceed { openRecordRouteActivity() }
            }
        } else {
            Toast.makeText(this, "Permissão de localização necessária", Toast.LENGTH_SHORT).show()
        }
    }

}
