package com.fullsekurity.theatreblood.donor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.databinding.DataBindingUtil
import androidx.databinding.OnRebindCallback
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.fullsekurity.theatreblood.R
import com.fullsekurity.theatreblood.activity.ActivityCallbacks
import com.fullsekurity.theatreblood.activity.MainActivity
import com.fullsekurity.theatreblood.databinding.DonorScreenBinding
import com.fullsekurity.theatreblood.repository.storage.Donor
import com.fullsekurity.theatreblood.ui.UIViewModel
import com.fullsekurity.theatreblood.utils.DaggerViewModelDependencyInjector
import com.fullsekurity.theatreblood.utils.ViewModelInjectorModule
import javax.inject.Inject

class DonorFragment : Fragment(), ActivityCallbacks {

    private lateinit var donorViewModel: DonorViewModel
    private lateinit var donor: Donor
    private lateinit var binding: DonorScreenBinding
    private lateinit var mainActivity: MainActivity

    companion object {
        fun newInstance(donor: Donor): DonorFragment {
            val fragment = DonorFragment()
            fragment.donor = donor
            return fragment
        }
    }

    @Inject
    lateinit var uiViewModel: UIViewModel

    override fun onAttach(context: Context) {
        DaggerViewModelDependencyInjector.builder()
            .viewModelInjectorModule(ViewModelInjectorModule(activity as MainActivity))
            .build()
            .inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        DaggerViewModelDependencyInjector.builder()
            .viewModelInjectorModule(ViewModelInjectorModule(activity as MainActivity))
            .build()
            .inject(this)
        binding = DataBindingUtil.inflate<ViewDataBinding>(inflater, R.layout.donor_screen, container, false) as DonorScreenBinding
        binding.lifecycleOwner = this
        donorViewModel = ViewModelProviders.of(this, DonorViewModelFactory(this)).get(DonorViewModel::class.java)
        binding.donorViewModel = donorViewModel
        binding.uiViewModel = uiViewModel
        uiViewModel.currentTheme = (activity as MainActivity).currentTheme
        binding.addOnRebindCallback(object :
            OnRebindCallback<ViewDataBinding>() {
            override fun onBound(binding: ViewDataBinding) {
                donorViewModel.isStable = true
            }
        })
        donorViewModel.setDonor(donor)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mainActivity = activity as MainActivity
    }

    override fun fetchActivity(): MainActivity {
        return if (::mainActivity.isInitialized) {
            mainActivity
        } else {
            activity as MainActivity
        }
    }

    override fun fetchRootView(): View {
        return binding.root
    }

    override fun fetchRadioButton(resId:Int): RadioButton {
        return fetchRootView().findViewById(resId)
    }

}