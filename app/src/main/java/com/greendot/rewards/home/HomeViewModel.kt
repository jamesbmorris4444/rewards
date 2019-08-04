package com.greendot.rewards.home

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import androidx.databinding.ObservableField
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.greendot.rewards.Constants
import com.greendot.rewards.activity.MainActivity
import com.greendot.rewards.repository.Movie
import com.squareup.picasso.Picasso
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*

class HomeViewModel(activity: MainActivity) : ViewModel() {
    private var homeDataModel = HomeDataModel()
    private val liveDataMovieList: MutableLiveData<ArrayList<Movie>> = MutableLiveData()
    var title: ObservableField<String>? = ObservableField("")
    var releaseDate: ObservableField<String>? = ObservableField("")
    var posterPath: ObservableField<String>? = ObservableField("")
    private var disposable: Disposable? = null
    private val mainActivity: MainActivity = activity

    companion object {
        @BindingAdapter("android:src")
        @JvmStatic
        fun setImageUrl(view: ImageView, url: String?) {
            if (url != null) {
                Picasso
                    .get()
                    .load(Constants.SMALL_IMAGE_URL_PREFIX + url)
                    .into(view)
            }
        }
    }

    init {
        disposable = homeDataModel.getMovieList()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe{ movieList -> liveDataMovieList.postValue(movieList) }
        homeDataModel.loadData()
    }

    fun unsubscribe() {
        disposable?.let { it.dispose() }
        disposable = null
    }

    fun getArrayListMovie(): LiveData<ArrayList<Movie>> {
        return liveDataMovieList
    }

    fun liveDataUpdate() {
        val rand = Random(System.currentTimeMillis())
        val movie: Movie? = liveDataMovieList.value?.let { it[rand.nextInt(20)] }
        title?.set(movie?.title)
        releaseDate?.set(movie?.releaseDate)
        posterPath?.set(movie?.posterPath)
    }

    fun onItemClick() {
        homeDataModel.loadData()
//        mainActivity.supportFragmentManager.beginTransaction()
//            .replace(R.id.fragments_container, HomeFragment.newInstance())
//            .addToBackStack(null)
//            .commitNow()
    }
}