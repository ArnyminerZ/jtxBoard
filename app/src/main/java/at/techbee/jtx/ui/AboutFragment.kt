package at.techbee.jtx.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import at.techbee.jtx.BuildConfig
import at.techbee.jtx.MainActivity
import at.techbee.jtx.R
import at.techbee.jtx.databinding.FragmentAboutBinding
import com.google.android.material.tabs.TabLayout
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
import java.text.SimpleDateFormat

class AboutFragment : Fragment() {

    lateinit var binding: FragmentAboutBinding
    lateinit var application: Application
    private lateinit var inflater: LayoutInflater

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // Get a reference to the binding object and inflate the fragment views.
        this.inflater = inflater
        this.binding = FragmentAboutBinding.inflate(inflater, container, false)
        this.application = requireNotNull(this.activity).application

        //val aboutBinding = FragmentAboutBinding.inflate(inflater, container, false)
        binding.fragmentAboutApp.aboutAppVersion.text = getString(R.string.about_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        binding.fragmentAboutApp.aboutAppBuildTime.text = getString(R.string.about_app_build_date, SimpleDateFormat.getDateInstance().format(BuildConfig.buildTime))

        // get the fragment for the about library
        val aboutLibrariesFragment = LibsBuilder()
            .withFields(R.string::class.java.fields)        // mandatory for non-standard build flavors
            .withLicenseShown(true)
            .withAboutIconShown(false)
            // https://github.com/mikepenz/AboutLibraries/issues/490
            .withLibraryModification("org_brotli__dec", Libs.LibraryFields.LIBRARY_NAME, "Brotli")
            .withLibraryModification("org_brotli__dec", Libs.LibraryFields.AUTHOR_NAME, "Google")
            .supportFragment()

        // add the about library fragment to the container view
        parentFragmentManager.beginTransaction()
            .add(binding.fragmentAboutLibrariesContainerView.id, aboutLibrariesFragment)
            .commit()

        binding.aboutTablayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

            override fun onTabSelected(tab: TabLayout.Tab?) {

                when (tab?.position) {
                    0 -> {
                        binding.fragmentAboutApp.aboutAppScrollview.visibility = View.VISIBLE
                        binding.fragmentAboutLibrariesContainerView.visibility = View.INVISIBLE
                    }
                    1 -> {
                        binding.fragmentAboutApp.aboutAppScrollview.visibility = View.INVISIBLE
                        binding.fragmentAboutLibrariesContainerView.visibility = View.INVISIBLE
                    } // TODO: replace with translations
                    2 -> {
                        binding.fragmentAboutApp.aboutAppScrollview.visibility = View.INVISIBLE
                        binding.fragmentAboutLibrariesContainerView.visibility = View.VISIBLE
                    }
                    else -> {
                        binding.fragmentAboutApp.aboutAppScrollview.visibility = View.INVISIBLE
                        binding.fragmentAboutLibrariesContainerView.visibility = View.INVISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {  /* nothing to do */  }
            override fun onTabReselected(tab: TabLayout.Tab?) {  /* nothing to do */  }
        })

        return binding.root
    }

    override fun onResume() {

        val activity = requireActivity() as MainActivity
        activity.setToolbarText("About")

        super.onResume()
    }

}