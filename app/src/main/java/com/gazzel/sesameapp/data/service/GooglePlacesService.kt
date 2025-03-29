package com.gazzel.sesameapp.data.service

import com.gazzel.sesameapp.domain.model.Place
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GooglePlacesService {
    @POST("v1/places:autocomplete")
    suspend fun getAutocompleteSuggestions(
        @Body request: AutocompleteRequest,
        @Query("key") apiKey: String
    ): Response<AutocompleteResponse>

    @GET("v1/places/{placeId}")
    suspend fun getPlaceDetails(
        @Path("placeId") placeId: String,
        @Query("key") apiKey: String,
        @Query("fields") fields: String = "id,displayName,formattedAddress,location,rating"
    ): Response<PlaceDetailsResponse>
}

data class AutocompleteRequest(
    val input: String,
    val sessionToken: String? = null
)

data class AutocompleteResponse(
    val suggestions: List<Suggestion>
)

data class Suggestion(
    val placePrediction: PlacePrediction
)

data class PlacePrediction(
    val placeId: String,
    val text: Text
)

data class Text(
    val text: String
)

data class PlaceDetailsResponse(
    val id: String,
    val displayName: DisplayName,
    val formattedAddress: String,
    val location: Location,
    val rating: Double?
)

data class DisplayName(
    val text: String,
    val languageCode: String
)

data class Location(
    val latitude: Double,
    val longitude: Double
) 