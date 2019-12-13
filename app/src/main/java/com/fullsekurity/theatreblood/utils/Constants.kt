package com.fullsekurity.theatreblood.utils

import com.fullsekurity.theatreblood.repository.storage.Donor

object Constants {

    const val MOVIES_ARRAY_DATA_TAG = "results"
    val DONOR_LIST_CLASS_TYPE = (ArrayList<Donor>()).javaClass
    const val THEATRE_BLOOD_BASE_URL = "https://api.themoviedb.org/3/discover/"
    private const val IMAGE_URL_PREFIX = "https://image.tmdb.org/t/p/"
    const val SMALL_IMAGE_URL_PREFIX = IMAGE_URL_PREFIX + "w300"
    const val BIG_IMAGE_URL_PREFIX = IMAGE_URL_PREFIX + "w500"
    const val API_KEY_REQUEST_PARAM = "api_key"
    const val LANGUAGE_REQUEST_PARAM = "language"
    const val PAGE_REQUEST_PARAM = "page"
    const val SORT_BY_PARAM = "sort_by"
    const val INCLUDE_ADULT_PARAM = "include_adult"
    const val INCLUDE_VIDEO_PARAM = "include_video"
    const val API_KEY = "17c5889b399d3c051099e4098ad83493"
    const val LANGUAGE = "en"
    const val DATA_BASE_NAME = "TheatreBlood.db"
    const val STANDARD_LEFT_AND_RIGHT_MARGIN = 20f
    const val STANDARD_EDIT_TEXT_HEIGHT = 60f
    const val STANDARD_BUTTON_HEIGHT = 50f
    const val EDIT_TEXT_TO_BUTTON_RATIO = 3  // 3:1

    // toolbar

    const val INITIAL_TOOLBAR_TITLE = "Theatre Blood"
    const val TOOLBAR_BACKGROUND_COLOR = "#e50239"
    const val TOOLBAR_TEXT_COLOR = "#ffffff"

}