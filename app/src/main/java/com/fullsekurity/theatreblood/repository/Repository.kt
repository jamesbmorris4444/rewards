package com.fullsekurity.theatreblood.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import com.fullsekurity.theatreblood.R
import com.fullsekurity.theatreblood.activity.ActivityCallbacks
import com.fullsekurity.theatreblood.activity.MainActivity
import com.fullsekurity.theatreblood.logger.LogUtils
import com.fullsekurity.theatreblood.logger.LogUtils.TagFilter.EXC
import com.fullsekurity.theatreblood.modal.StandardModal
import com.fullsekurity.theatreblood.repository.network.APIClient
import com.fullsekurity.theatreblood.repository.network.APIInterface
import com.fullsekurity.theatreblood.repository.storage.BloodDatabase
import com.fullsekurity.theatreblood.repository.storage.Donor
import com.fullsekurity.theatreblood.repository.storage.DonorWithProducts
import com.fullsekurity.theatreblood.repository.storage.Product
import com.fullsekurity.theatreblood.utils.Constants
import com.fullsekurity.theatreblood.utils.Constants.MAIN_DATABASE_NAME
import com.fullsekurity.theatreblood.utils.Constants.MODIFIED_DATABASE_NAME
import com.fullsekurity.theatreblood.utils.Utils
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.concurrent.TimeUnit


class Repository(private val activityCallbacks: ActivityCallbacks) {

    private val TAG = Repository::class.java.simpleName
    lateinit var mainBloodDatabase: BloodDatabase
    lateinit var stagingBloodDatabase: BloodDatabase
    private val donorsService: APIInterface = APIClient.client
    private var transportType = TransportType.NONE
    private var isMetered: Boolean = false
    private var cellularNetwork: Network? = null
    private var wiFiNetwork: Network? = null
    var isOfflineMode = true
    val liveViewDonorList: MutableLiveData<List<Donor>> = MutableLiveData()

    fun setBloodDatabase(context: Context) {
        BloodDatabase.newInstance(context, MAIN_DATABASE_NAME)?.let {
            mainBloodDatabase = it
        }
        BloodDatabase.newInstance(context, MODIFIED_DATABASE_NAME)?.let {
            stagingBloodDatabase = it
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
        val connectivityManager = activityCallbacks.fetchActivity().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
                        activityCallbacks.fetchActivity().setToolbarNetworkStatus()
                    } else if (networkCapabiities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        cellularNetwork = network
                        transportType = TransportType.CELLULAR
                        activityCallbacks.fetchActivity().setToolbarNetworkStatus()
                    }
                }
            }
            TransportType.WIFI -> {
                connectivityManager.getNetworkCapabilities(network)?.let { networkCapabiities ->
                    if (networkCapabiities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        cellularNetwork = network
                        transportType = TransportType.BOTH
                        activityCallbacks.fetchActivity().setToolbarNetworkStatus()
                    }
                }
            }
            TransportType.CELLULAR -> {
                connectivityManager.getNetworkCapabilities(network)?.let { networkCapabiities ->
                    if (networkCapabiities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        wiFiNetwork = network
                        transportType = TransportType.BOTH
                        activityCallbacks.fetchActivity().setToolbarNetworkStatus()
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
                activityCallbacks.fetchActivity().setToolbarNetworkStatus()
            }
            TransportType.BOTH -> {
                val connectivityManager = activityCallbacks.fetchActivity().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.getNetworkCapabilities(wiFiNetwork)?.let { networkCapabiities ->
                    if (networkCapabiities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        transportType = TransportType.WIFI
                        activityCallbacks.fetchActivity().setToolbarNetworkStatus()
                    }
                }
                if (transportType == TransportType.BOTH) {
                    transportType = TransportType.CELLULAR
                    activityCallbacks.fetchActivity().setToolbarNetworkStatus()
                }
                activityCallbacks.fetchActivity().setToolbarNetworkStatus()
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

    // The code below here refreshes the main donations base

    fun refreshDatabase(progressBar: ProgressBar, activity: MainActivity) {
        saveDatabase(activity, MAIN_DATABASE_NAME)
        deleteDatabase(activity, MAIN_DATABASE_NAME)
        var disposable: Disposable? = null
        disposable = donorsService.getDonors(Constants.API_KEY, Constants.LANGUAGE, 13)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .timeout(15L, TimeUnit.SECONDS)
            .subscribe ({ donorResponse ->
                disposable?.dispose()
                initializeDataBase(progressBar, donorResponse.results, donorResponse.products, activity)
            },
            { throwable ->
                progressBar.visibility = View.GONE
                disposable?.dispose()
                initializeDatabaseFailureModal(activity, throwable.message)
            })
    }

    private fun initializeDataBase(progressBar: ProgressBar, donors: List<Donor>, products: List<List<Product>>, activity: MainActivity) {
        for (donorIndex in donors.indices) {
            for (productIndex in products[donorIndex].indices) {
                products[donorIndex][productIndex].donorId = donors[donorIndex].id
            }
        }
        insertDonorsAndProductsIntoLocalDatabase(progressBar, mainBloodDatabase, donors, products, activity)
    }

    private fun insertDonorsAndProductsIntoLocalDatabase(progressBar: ProgressBar, database: BloodDatabase, donors: List<Donor>, products: List<List<Product>>, activity: MainActivity) {
        var disposable: Disposable? = null
        disposable = Completable.fromAction { database.databaseDao().insertDonorsAndProductLists(donors, products) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe ({
                disposable?.dispose()
                progressBar.visibility = View.GONE
                liveViewDonorList.postValue(donors)
                StandardModal(
                    activity,
                    modalType = StandardModal.ModalType.STANDARD,
                    titleText = activity.getString(R.string.std_modal_refresh_success_title),
                    bodyText = String.format(activity.getString(R.string.std_modal_refresh_success_body, activity.getDatabasePath(MAIN_DATABASE_NAME))),
                    positiveText = activity.getString(R.string.std_modal_ok),
                    dialogFinishedListener = object : StandardModal.DialogFinishedListener {
                        override fun onPositive(string: String) { }
                        override fun onNegative() { }
                        override fun onNeutral() { }
                        override fun onBackPressed() { }
                    }
                ).show(activity.supportFragmentManager, "MODAL")
            },
            { throwable ->
                disposable?.dispose()
                progressBar.visibility = View.GONE
                LogUtils.E(LogUtils.FilterTags.withTags(EXC), "insertDonorsAndProductsIntoLocalDatabase", throwable)

            })
    }

    private fun initializeDatabaseFailureModal(activity: MainActivity, errorMessage: String?) {
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
                override fun onPositive(string: String) {
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

    private fun deleteDatabase(context: Context, databaseName: String) {
        context.deleteDatabase(databaseName)
    }

    private fun saveDatabase(context: Context, databaseName: String) {
        val db: File = context.getDatabasePath(databaseName)
        val dbShm = File(db.parent, "$databaseName-shm")
        val dbWal = File(db.parent, "$databaseName-wal")
        val dbBackup = File(db.parent, "$databaseName-backup")
        val dbShmBackup = File(db.parent, "$databaseName-backup-shm")
        val dbWalBackup = File(db.parent, "$databaseName-backup-wal")
        if (db.exists()) {
            db.copyTo(dbBackup, true)
        }
        if (dbShm.exists()) {
            dbShm.copyTo(dbShmBackup, true)
        }
        if (dbWal.exists()) {
            dbWal.copyTo(dbWalBackup, true)
        }
        LogUtils.D(TAG, LogUtils.FilterTags.withTags(LogUtils.TagFilter.DAO), String.format("Path Name \"%s\" exists and was backed up", db.toString()))
    }

    // The code below here does CRUD on the database

    fun insertDonorIntoDatabase(database: BloodDatabase, donor: Donor, transitionToCreateDonation: Boolean) {
        var disposable: Disposable? = null
        disposable = Completable.fromAction { database.databaseDao().insertDonor(donor) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe ({
                disposable?.dispose()
                StandardModal(
                    activityCallbacks,
                    modalType = StandardModal.ModalType.STANDARD,
                    titleText = activityCallbacks.fetchActivity().getString(R.string.std_modal_insert_donor_staging_title),
                    bodyText = activityCallbacks.fetchActivity().getString(R.string.std_modal_insert_donor_staging_body),
                    positiveText = activityCallbacks.fetchActivity().getString(R.string.std_modal_ok),
                    dialogFinishedListener = object : StandardModal.DialogFinishedListener {
                        override fun onPositive(string: String) {
                            if (transitionToCreateDonation) {
                                activityCallbacks.fetchActivity().loadCreateProductsFragment(donor)
                            } else {
                                activityCallbacks.fetchActivity().onBackPressed()
                            }
                        }
                        override fun onNegative() { }
                        override fun onNeutral() { }
                        override fun onBackPressed() {
                            activityCallbacks.fetchActivity().onBackPressed()
                        }
                    }
                ).show(activityCallbacks.fetchActivity().supportFragmentManager, "MODAL")
            },
            { throwable ->
                disposable?.dispose()
                insertDonorIntoDatabaseFailure(transitionToCreateDonation, donor, "insertDonorIntoDatabase", throwable)
            })
    }

    private fun insertDonorIntoDatabaseFailure(transition: Boolean, donor: Donor, method: String, throwable: Throwable) {
        LogUtils.E(LogUtils.FilterTags.withTags(EXC), method, throwable)
        if (transition) {
            activityCallbacks.fetchActivity().loadCreateProductsFragment(donor)
        } else {
            activityCallbacks.fetchActivity().onBackPressed()
        }
    }

    fun insertDonorAndProductsIntoDatabase(database: BloodDatabase, donor: Donor, products: List<Product>) {
        var disposable: Disposable? = null
        disposable = Completable.fromAction { database.databaseDao().insertDonorAndProducts(donor, products) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe ({
                disposable?.dispose()
                StandardModal(
                    activityCallbacks,
                    modalType = StandardModal.ModalType.STANDARD,
                    titleText = activityCallbacks.fetchActivity().getString(R.string.std_modal_insert_products_staging_title),
                    bodyText = activityCallbacks.fetchActivity().getString(R.string.std_modal_insert_products_staging_body),
                    positiveText = activityCallbacks.fetchActivity().getString(R.string.std_modal_ok),
                    dialogFinishedListener = object : StandardModal.DialogFinishedListener {
                        override fun onPositive(string: String) {
                            activityCallbacks.fetchActivity().supportFragmentManager.popBackStack(Constants.ROOT_FRAGMENT_TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                            activityCallbacks.fetchActivity().loadDonateProductsFragment(true)
                        }
                        override fun onNegative() { }
                        override fun onNeutral() { }
                        override fun onBackPressed() {
                            activityCallbacks.fetchActivity().supportFragmentManager.popBackStack(Constants.ROOT_FRAGMENT_TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                            activityCallbacks.fetchActivity().loadDonateProductsFragment(true)
                        }
                    }
                ).show(activityCallbacks.fetchActivity().supportFragmentManager, "MODAL")
            },
            { throwable ->
                disposable?.dispose()
                insertDonorAndProductsIntoDatabaseFailure("insertDonorAndProductsIntoDatabase", throwable)
            })
    }

    private fun insertDonorAndProductsIntoDatabaseFailure(method: String, throwable: Throwable) {
        LogUtils.E(LogUtils.FilterTags.withTags(EXC), method, throwable)
        activityCallbacks.fetchActivity().supportFragmentManager.popBackStack(Constants.ROOT_FRAGMENT_TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        activityCallbacks.fetchActivity().loadDonateProductsFragment(true)
    }

    fun insertReassociatedProductsIntoDatabase(database: BloodDatabase, products: List<Product>, initializeView: () -> Unit) {
        var disposable: Disposable? = null
        disposable = Completable.fromAction { database.databaseDao().insertProducts(products) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe ({
                disposable?.dispose()
                StandardModal(
                    activityCallbacks,
                    modalType = StandardModal.ModalType.STANDARD,
                    titleText = activityCallbacks.fetchActivity().getString(R.string.std_modal_insert_products_staging_title),
                    bodyText = activityCallbacks.fetchActivity().getString(R.string.std_modal_insert_products_staging_body),
                    positiveText = activityCallbacks.fetchActivity().getString(R.string.std_modal_ok),
                    dialogFinishedListener = object : StandardModal.DialogFinishedListener {
                        override fun onPositive(string: String) {
                            initializeView()
                        }
                        override fun onNegative() { }
                        override fun onNeutral() { }
                        override fun onBackPressed() {
                            initializeView()
                        }
                    }
                ).show(activityCallbacks.fetchActivity().supportFragmentManager, "MODAL")
            },
            { throwable ->
                disposable?.dispose()
                insertReassociatedProductsIntoDatabaseFailure("insertReassociatedProductsIntoDatabase", throwable, initializeView)
            })
    }

    private fun insertReassociatedProductsIntoDatabaseFailure(method: String, throwable: Throwable, initializeView: () -> Unit) {
        LogUtils.E(LogUtils.FilterTags.withTags(EXC), method, throwable)
        initializeView()
    }
    
    fun databaseCounts() {
        val entryCountList = listOf(
            databaseDonorCount(stagingBloodDatabase),
            databaseDonorCount(mainBloodDatabase)
        )
        var disposable: Disposable? = null
        disposable = Single.zip(entryCountList) { args -> listOf(args) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ responseList ->
                disposable?.dispose()
                val response = responseList[0]
                getProductEntryCount(response[0] as Int, response[1] as Int)
            },
            { throwable ->
                disposable?.dispose()
                LogUtils.E(LogUtils.FilterTags.withTags(EXC), "databaseCounts", throwable)
            })
    }

    private fun getProductEntryCount(modifiedDonors: Int, mainDonors: Int) {
        val entryCountList = listOf(
            databaseProductCount(stagingBloodDatabase),
            databaseProductCount(mainBloodDatabase)
        )
        var disposable: Disposable? = null
        disposable = Single.zip(entryCountList) { args -> listOf(args) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ responseList ->
                disposable?.dispose()
                val response = responseList[0]
                StandardModal(
                    activityCallbacks,
                    modalType = StandardModal.ModalType.STANDARD,
                    titleText = activityCallbacks.fetchActivity().getString(R.string.std_modal_staging_database_count_title),
                    bodyText = String.format(activityCallbacks.fetchActivity().getString(R.string.std_modal_staging_database_count_body), modifiedDonors, mainDonors, response[0] as Int, response[1] as Int),
                    positiveText = activityCallbacks.fetchActivity().getString(R.string.std_modal_ok),
                    dialogFinishedListener = object : StandardModal.DialogFinishedListener {
                        override fun onPositive(password: String) { }
                        override fun onNegative() { }
                        override fun onNeutral() { }
                        override fun onBackPressed() { }
                    }
                ).show(activityCallbacks.fetchActivity().supportFragmentManager, "MODAL")
            },
            { throwable ->
                disposable?.dispose()
                LogUtils.E(LogUtils.FilterTags.withTags(EXC), "getProductEntryCount", throwable)
            })
    }

    private fun databaseDonorCount(database: BloodDatabase): Single<Int> {
        return database.databaseDao().getDonorEntryCount()
    }

    private fun databaseProductCount(database: BloodDatabase): Single<Int> {
        return database.databaseDao().getProductEntryCount()
    }

    @Suppress("UNCHECKED_CAST")
    fun handleSearchClick(view: View, searchKey: String, showDonors: (donorList: List<Donor>) -> Unit) {
        val fullNameResponseList = listOf(
            donorsFromFullName(mainBloodDatabase, searchKey),
            donorsFromFullName(stagingBloodDatabase, searchKey)
        )
        var disposable: Disposable? = null
        disposable = Single.zip(fullNameResponseList) { args -> listOf(args) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe({ responseList ->
                disposable?.dispose()
                val response = responseList[0]
                val stagingDatabaseList = response[1] as List<Donor>
                val mainDatabaseList = response[0] as List<Donor>
                val newList = stagingDatabaseList.union(mainDatabaseList).distinctBy { donor -> Utils.donorUnionStringForDistinctBy(donor) }
                showDonors(newList)
            },
            { throwable ->
                disposable?.dispose()
                LogUtils.E(LogUtils.FilterTags.withTags(EXC), "handleSearchClick", throwable)
            })
    }

    private fun donorsFromFullName(database: BloodDatabase, search: String): Single<List<Donor>> {
        val searchLast: String
        var searchFirst = "%"
        val index = search.indexOf(',')
        if (index < 0) {
            searchLast = "$search%"
        } else {
            val last = search.substring(0, index)
            val first = search.substring(index + 1)
            searchFirst = "$first%"
            searchLast = "$last%"
        }
        return database.databaseDao().donorsFromFullName(searchLast, searchFirst)
    }

    fun handleReassociateSearchClick(view: View, searchKey: String, showDonorsAndProducts: (donorsAndProductsList: List<DonorWithProducts>) -> Unit) {
        var disposable: Disposable? = null
        disposable = donorsFromFullNameWithProducts(stagingBloodDatabase, searchKey)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe ({ donorWithProducts ->
                disposable?.dispose()
                showDonorsAndProducts(donorWithProducts)
            },
            { throwable ->
                disposable?.dispose()
                LogUtils.E(LogUtils.FilterTags.withTags(EXC), "handleReassociateSearchClick", throwable)
            })

    }

    private fun donorsFromFullNameWithProducts(database: BloodDatabase, search: String): Single<List<DonorWithProducts>> {
        var searchLast: String
        var searchFirst = "%"
        val index = search.indexOf(',')
        if (index < 0) {
            searchLast = "$search%"
        } else {
            val last = search.substring(0, index)
            val first = search.substring(index + 1)
            searchFirst = "$first%"
            searchLast = "$last%"
        }
        return database.databaseDao().donorsFromFullNameWithProducts(searchLast, searchFirst)
    }

}