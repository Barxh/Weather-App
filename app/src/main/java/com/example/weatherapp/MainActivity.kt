package com.example.weatherapp

import android.content.Context
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.karumi.dexter.Dexter
import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.example.weatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson

import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.weatherapp.models.WeatherResponse
import com.weatherapp.network.WeatherService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var  fusedLocationClient: FusedLocationProviderClient

    private lateinit var binding: ActivityMainBinding
    private var progressDialog: Dialog? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            val lastLocation: Location = result.lastLocation!!
            val latitude = lastLocation.latitude
            val longitude = lastLocation.longitude

            getLocationWeatherDetails(latitude,longitude)
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupDisplay()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if(!isLocationEnabled()){
            Toast.makeText(this, "Please, turn on location", Toast.LENGTH_LONG).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            askForPermissions()
        }
    }


    private fun askForPermissions() {
        Dexter.withContext(this).withPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {

                if (report!!.areAllPermissionsGranted()) {
                    requestLocationData()

                }


            }

            override fun onPermissionRationaleShouldBeShown(
                p0: MutableList<PermissionRequest>?,
                p1: PermissionToken?
            ) {
                Log.e("permissions", "onPermissionsChecked: rational", )


                setupAlertDialog()
            }

        }).onSameThread().check()
    }


    private fun setupAlertDialog() {
        AlertDialog.Builder(this@MainActivity)
            .setMessage("It looks like you have turned off permissions required for this feature.")
            .setPositiveButton("GO TO SETTINGS") { dialog, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }

            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()
            }.show()
    }


    private fun getLocationWeatherDetails(latitude: Double, longitude:Double){
        if(Constants.isNetworkAvailable(this)){

            val retrofit : Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service : WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall : Call<WeatherResponse> = service.getWeather(
                latitude, longitude,Constants.METRIC_UNIT, Constants.APP_ID
            )
            showCustomProgressDialog()
            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    hideProgressDialog()
                    if (response.isSuccessful){
                        val weatherList: WeatherResponse = response.body()!!
                        val weatherResponseJson = Gson().toJson(weatherList)
                        val editor = sharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJson)

                        editor.apply()

                        setupDisplay()
                    }else{
                        val rc = response.code()
                        when(rc){
                            400->{}
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {

                    hideProgressDialog()
                }

            })
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000L).build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())


    }
    private fun isLocationEnabled(): Boolean{
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    private fun showCustomProgressDialog(){
        progressDialog = Dialog(this)
        progressDialog!!.setContentView(R.layout.custom_progress_dialog)

        progressDialog!!.show()
    }
    private fun hideProgressDialog(){
        if(progressDialog!=null){
            progressDialog!!.dismiss()
        }
    }

    private fun getUnit(toString: String): String {

        var value = "°C"
        if("US" == toString || "LR" == toString || "MM" == toString){
            value = "°F"
        }
        return value
    }

    private fun setupDisplay(){

        val weatherResponseJsonString = sharedPreferences
            .getString(Constants.WEATHER_RESPONSE_DATA, "")
        if(!weatherResponseJsonString.isNullOrEmpty()){

            val weatherResponse = Gson().fromJson(weatherResponseJsonString,
            WeatherResponse::class.java)
            for( i in weatherResponse.weather.indices){


                binding.tvMain.text = weatherResponse.weather[i].main
                binding.tvMainDescription.text = weatherResponse.weather[i].description
                binding.tvTemp.text = weatherResponse.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                binding.tvSunriseTime.text = unixTime(weatherResponse.sys.sunrise)
                binding.tvSunsetTime.text = unixTime(weatherResponse.sys.sunset)

                binding.tvHumidity.text = weatherResponse.main.humidity.toString() + " per cent"
                binding.tvMin.text = weatherResponse.main.temp_min.toString() + " min"
                binding.tvMax.text = weatherResponse.main.temp_max.toString() + "  max"
                binding.tvSpeed.text = weatherResponse.wind.speed.toString()
                binding.tvName.text = weatherResponse.name
                binding.tvCountry.text = weatherResponse.sys.country

                when(weatherResponse.weather[i].icon){
                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                    "12d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "12n" -> binding.ivMain.setImageResource(R.drawable.snowflake)

                }
            }
        }



    }

    private fun unixTime(time: Long): String?{
        val date = Date(time*1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.button_refresh -> {requestLocationData()
                true}
            else ->super.onOptionsItemSelected(item)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }
}