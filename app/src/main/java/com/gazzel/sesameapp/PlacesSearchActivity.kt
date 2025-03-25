package com.gazzel.sesameapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth

class PlacesSearchActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var placesApiService: PlacesApiService
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(750, TimeUnit.MILLISECONDS)
            .writeTimeout(500, TimeUnit.MILLISECONDS)
            .build()

        placesApiService = Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlacesApiService::class.java)

        val listId = intent.getIntExtra("listId", -1)

        setContent {
            SesameAppTheme {
                SearchPlacesScreen(
                    onSkip = { finish() },
                    onPlaceSelected = { place, userRating, visitStatus ->
                        lifecycleScope.launch {
                            val token = getValidToken()
                            if (token != null) {
                                try {
                                    val listService = Retrofit.Builder()
                                        .baseUrl("https://gazzel.io/")
                                        .client(okHttpClient)
                                        .addConverterFactory(GsonConverterFactory.create())
                                        .build()
                                        .create(ListService::class.java)

                                    val response = listService.addPlace(
                                        listId,
                                        PlaceCreate(
                                            placeId = place.id,
                                            name = place.displayName.text,
                                            address = place.formattedAddress,
                                            latitude = place.location.latitude,
                                            longitude = place.location.longitude,
                                            rating = userRating, // User's qualitative rating
                                            notes = null, // Don't store the Google Places rating
                                            visitStatus = visitStatus // Pass the user-selected visit status
                                        ),
                                        "Bearer $token"
                                    )
                                    if (response.isSuccessful) {
                                        Log.d("FastAPI", "Place added successfully to list $listId with rating: $userRating and visit status: $visitStatus")
                                        PlaceUpdateManager.notifyPlaceAdded() // Notify that a new place was added
                                        finish()
                                    } else {
                                        Log.e(
                                            "FastAPI",
                                            "Failed to add place: ${response.code()} - ${response.errorBody()?.string()}"
                                        )
                                        runOnUiThread {
                                            android.widget.Toast.makeText(
                                                this@PlacesSearchActivity,
                                                "Failed to add place: ${response.message()}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("FastAPI", "Exception adding place: ${e.message}", e)
                                    runOnUiThread {
                                        android.widget.Toast.makeText(
                                            this@PlacesSearchActivity,
                                            "Failed to add place: ${e.message}",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    },
                    placesApiService = placesApiService,
                    apiKey = getString(R.string.google_maps_key)
                )
            }
        }
    }

    private suspend fun getValidToken(): String? {
        return if (cachedToken != null && System.currentTimeMillis() / 1000 < tokenExpiry - 300) {
            cachedToken
        } else {
            refreshToken()
        }
    }

    private suspend fun refreshToken(): String? {
        return try {
            val result = auth.currentUser?.getIdToken(true)?.await()
            cachedToken = result?.token
            tokenExpiry = result?.expirationTimestamp ?: 0
            Log.d("FastAPI", "Refreshed token: $cachedToken, expires: $tokenExpiry")
            cachedToken
        } catch (e: Exception) {
            Log.e("FastAPI", "Token refresh failed: ${e.message}", e)
            null
        }
    }
}