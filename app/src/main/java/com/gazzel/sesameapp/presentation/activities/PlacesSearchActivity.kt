package com.gazzel.sesameapp.presentation.activities

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.gazzel.sesameapp.R
import com.gazzel.sesameapp.data.manager.PlaceUpdateManager
import com.gazzel.sesameapp.data.service.GooglePlacesService
import com.gazzel.sesameapp.data.service.PlaceCreate
import com.gazzel.sesameapp.data.service.PlaceDetailsResponse
import com.gazzel.sesameapp.data.service.UserListService
import com.gazzel.sesameapp.presentation.screens.SearchPlacesScreen
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.gazzel.sesameapp.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class PlacesSearchActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googlePlacesService: GooglePlacesService
    private lateinit var listService: UserListService // Changed to UserListService
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10000, TimeUnit.MILLISECONDS)
            .readTimeout(10000, TimeUnit.MILLISECONDS)
            .writeTimeout(10000, TimeUnit.MILLISECONDS)
            .build()

        googlePlacesService = Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GooglePlacesService::class.java)

        listService = Retrofit.Builder()
            .baseUrl("https://gazzel.io/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserListService::class.java)

        val listId = intent.getStringExtra("listId") ?: "" // Changed to String

        setContent {
            SesameAppTheme {
                SearchPlacesScreen(
                    onSkip = { finish() },
                    onPlaceSelected = { placeDetailsResponse: PlaceDetailsResponse, userRating, visitStatus ->
                        lifecycleScope.launch {
                            val token = getValidToken()
                            if (token != null) {
                                try {
                                    val response = listService.addPlace(
                                        listId = listId,
                                        place = PlaceCreate(
                                            placeId = placeDetailsResponse.id,
                                            name = placeDetailsResponse.displayName.text,
                                            address = placeDetailsResponse.formattedAddress,
                                            latitude = placeDetailsResponse.location.latitude,
                                            longitude = placeDetailsResponse.location.longitude,
                                            rating = userRating
                                        ),
                                        authorization = "Bearer $token"
                                    )
                                    if (response.isSuccessful) {
                                        Log.d("FastAPI", "Place added successfully to list $listId with rating: $userRating and visit status: $visitStatus")
                                        PlaceUpdateManager.notifyPlaceAdded()
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
                    googlePlacesService = googlePlacesService,
                    apiKey = getString(R.string.google_api_key)
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