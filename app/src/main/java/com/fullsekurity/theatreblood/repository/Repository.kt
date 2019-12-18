package com.fullsekurity.theatreblood.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.View
import android.widget.ProgressBar
import com.fullsekurity.theatreblood.R
import com.fullsekurity.theatreblood.activity.MainActivity
import com.fullsekurity.theatreblood.logger.LogUtils
import com.fullsekurity.theatreblood.modal.StandardModal
import com.fullsekurity.theatreblood.repository.network.APIClient
import com.fullsekurity.theatreblood.repository.network.APIInterface
import com.fullsekurity.theatreblood.repository.storage.BloodDatabase
import com.fullsekurity.theatreblood.repository.storage.Donor
import com.fullsekurity.theatreblood.utils.Constants
import com.fullsekurity.theatreblood.utils.Constants.DATA_BASE_NAME
import com.fullsekurity.theatreblood.utils.Constants.INSERTED_DATA_BASE_NAME
import com.fullsekurity.theatreblood.utils.Constants.MODIFIED_DATA_BASE_NAME
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.concurrent.TimeUnit


class Repository(val activity: MainActivity) {

    private val TAG = Repository::class.java.simpleName
    private lateinit var bloodDatabase: BloodDatabase
    private lateinit var modifiedDatabase: BloodDatabase
    private lateinit var insertedDatabase: BloodDatabase
    private val donorsService: APIInterface = APIClient.client
    private var disposable: Disposable? = null
    private var transportType = TransportType.NONE
    private var isMetered: Boolean = false
    private var cellularNetwork: Network? = null
    private var wiFiNetwork: Network? = null
    var isOfflineMode = true

    fun setBloodDatabase(context: Context) {
        bloodDatabase = BloodDatabase.newInstance(context, DATA_BASE_NAME)
        modifiedDatabase = BloodDatabase.newInstance(context, MODIFIED_DATA_BASE_NAME)
        insertedDatabase = BloodDatabase.newInstance(context, INSERTED_DATA_BASE_NAME)
    }

    fun onCleared() {
        disposable?.let {
            it.dispose()
            disposable = null
        }
    }

    // The code below here manages the network status

    private enum class TransportType {
        NONE,
        CELLULAR,
        WIFI,
        BOTH
    }

    init {
        val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder: NetworkRequest.Builder = NetworkRequest.Builder()
        connectivityManager.registerNetworkCallback(
            builder.build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onAvailableHelper(connectivityManager, network)
                }

                override fun onLost(network: Network) {
                    onLostHelper()
                }
            }
        )
    }

    private fun onAvailableHelper(connectivityManager: ConnectivityManager, network: Network) {
        isOfflineMode = false
        setConnectedTransportType(connectivityManager, network)
        isMetered = connectivityManager.isActiveNetworkMetered
        LogUtils.W(TAG, LogUtils.FilterTags.withTags(LogUtils.TagFilter.NET), String.format("Network is connected, TYPE: %s (metered=%b)", transportType.name, isMetered))
    }

    private fun onLostHelper() {
        isOfflineMode = false
        setDisconnectedTransportType()
        isMetered = false
        LogUtils.W(TAG, LogUtils.FilterTags.withTags(LogUtils.TagFilter.NET), String.format("Network connectivity is lost, TYPE: %s (metered=%b)", transportType.name, isMetered))
    }


    private fun setConnectedTransportType(connectivityManager: ConnectivityManager, network: Network) {
        when (transportType) {
            TransportType.NONE -> {
                connectivityManager.getNetworkCapabilities(network)?.let { networkCapabiities ->
                    if (networkCapabiities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        wiFiNetwork = network
                        transportType = TransportType.WIFI
                        activity.setToolbarNetworkStatus()
                    } else if (networkCapabiities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        cellularNetwork = network
                        transportType = TransportType.CELLULAR
                        activity.setToolbarNetworkStatus()
                    }
                }
            }
            TransportType.WIFI -> {
                connectivityManager.getNetworkCapabilities(network)?.let { networkCapabiities ->
                    if (networkCapabiities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        cellularNetwork = network
                        transportType = TransportType.BOTH
                        activity.setToolbarNetworkStatus()
                    }
                }
            }
            TransportType.CELLULAR -> {
                connectivityManager.getNetworkCapabilities(network)?.let { networkCapabiities ->
                    if (networkCapabiities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        wiFiNetwork = network
                        transportType = TransportType.BOTH
                        activity.setToolbarNetworkStatus()
                    }
                }

            }
            TransportType.BOTH -> { }
        }
    }

    private fun setDisconnectedTransportType() {
        when (transportType) {
            TransportType.NONE -> { }
            TransportType.WIFI, TransportType.CELLULAR -> {
                transportType = TransportType.NONE
                activity.setToolbarNetworkStatus()
            }
            TransportType.BOTH -> {
                val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.getNetworkCapabilities(wiFiNetwork)?.let { networkCapabiities ->
                    if (networkCapabiities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        transportType = TransportType.WIFI
                        activity.setToolbarNetworkStatus()
                    }
                }
                if (transportType == TransportType.BOTH) {
                    transportType = TransportType.CELLULAR
                    activity.setToolbarNetworkStatus()
                }
                activity.setToolbarNetworkStatus()
            }
        }
    }

    fun getNetworkStatusResInt(): Int {
        when (transportType) {
            TransportType.NONE -> {
                return R.drawable.ic_network_status_none
            }
            TransportType.CELLULAR -> {
                return R.drawable.ic_network_status_cellular
            }
            TransportType.BOTH, TransportType.WIFI -> {
                return R.drawable.ic_network_status_wifi
            }
        }
    }

    // The code below here refreshes the data base

    fun refreshDatabase(progressBar: ProgressBar, activity: MainActivity) {
        saveDatabase(activity)
        deleteDatabase(activity)
        disposable = donorsService.getDonors(Constants.API_KEY, Constants.LANGUAGE, 10)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .timeout(15L, TimeUnit.SECONDS)
            .subscribe ({ donorResponse ->
                progressBar.visibility = View.GONE
                initializeDataBase(donorResponse.results, activity)
            },
            {
                throwable -> initalizeDatabaseFailureModal(activity, throwable.message)
            })
    }

    private fun initalizeDatabaseFailureModal(activity: MainActivity, errorMessage: String?) {
        var error = errorMessage
        if (error == null) {
            error = "App cannot continue"
        }
        StandardModal(
            activity,
            modalType = StandardModal.ModalType.STANDARD,
            iconType = StandardModal.IconType.ERROR,
            titleText = activity.getString(R.string.std_modal_initialize_database_failure_title),
            bodyText = error,
            positiveText = activity.getString(R.string.std_modal_ok),
            dialogFinishedListener = object : StandardModal.DialogFinishedListener {
                override fun onPositive(password: String) {
                    activity.finishActivity()
                }
                override fun onNegative() { }
                override fun onNeutral() { }
                override fun onBackPressed() {
                    activity.finishActivity()
                }
            }
        ).show(activity.supportFragmentManager, "MODAL")
    }

    private fun initializeDataBase(donors: List<Donor>, activity: MainActivity) {
        for (entry in donors.indices) {
            insertIntoDatabase(donors[entry])
        }
        StandardModal(
            activity,
            modalType = StandardModal.ModalType.STANDARD,
            titleText = activity.getString(R.string.std_modal_refresh_success_title),
            bodyText = String.format(activity.getString(R.string.std_modal_refresh_success_body, activity.getDatabasePath(DATA_BASE_NAME))),
            positiveText = activity.getString(R.string.std_modal_ok),
            dialogFinishedListener = object : StandardModal.DialogFinishedListener {
                override fun onPositive(password: String) { }
                override fun onNegative() { }
                override fun onNeutral() { }
                override fun onBackPressed() { }
            }
        ).show(activity.supportFragmentManager, "MODAL")
    }

    private fun deleteDatabase(context: Context) {
        context.deleteDatabase(DATA_BASE_NAME)
    }

    private fun saveDatabase(context: Context) {
        val db = context.getDatabasePath(DATA_BASE_NAME)
        val dbBackup = File(db.parent, DATA_BASE_NAME+"_backup")
        if (db.exists()) {
            db.copyTo(dbBackup, true)
            LogUtils.D(TAG, LogUtils.FilterTags.withTags(LogUtils.TagFilter.ANX), String.format("Path Name \"%s\" exists and was backed up", db.toString()))
        }
    }

    fun closeDatabase() {
        bloodDatabase.let { bloodDatabase ->
            if (bloodDatabase.isOpen) {
                bloodDatabase.close()
            }
        }
        modifiedDatabase.let { bloodDatabase ->
            if (bloodDatabase.isOpen) {
                bloodDatabase.close()
            }
        }
        insertedDatabase.let { bloodDatabase ->
            if (bloodDatabase.isOpen) {
                bloodDatabase.close()
            }
        }
    }

    // The code below here dues CRUD on the database

    private fun insertIntoDatabase(donor: Donor) {
        bloodDatabase.donorDao().insertDonor(donor)
    }

    fun insertIntoModifiedDatabase(donor: Donor) {
        modifiedDatabase.donorDao().insertDonor(donor)
    }

    fun insertIntoInsertedDatabase(donor: Donor) {
        insertedDatabase.donorDao().insertDonor(donor)
    }

    fun donorsFromFullName(search: String): List<Donor> {
        var searchLast: String
        var searchFirst = "%"
        val index = search.indexOf(',')
        if (index < 0) {
            searchLast = "%$search%"
        } else {
            val last = search.substring(0, index)
            val first = search.substring(index + 1)
            searchFirst = "%$first%"
            searchLast = "%$last%"
        }
        var retval: List<Donor> = arrayListOf()
        bloodDatabase.donorDao()?.donorsFromFullName(searchLast, searchFirst)?.let {
            retval = it
        }
        return retval
    }

}