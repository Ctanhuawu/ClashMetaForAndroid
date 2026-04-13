package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.github.kr328.clash.design.databinding.DesignSettingsBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartNetwork, StartLogs, StartOverride, StartMetaFeature,
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)
        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)
        binding.activityBarLayout.findViewById<ImageView>(R.id.activity_bar_close_view)?.visibility = View.GONE
        binding.activityBarLayout.findViewById<TextView>(R.id.activity_bar_title_view)?.apply {
            setText(R.string.settings)
            setPaddingRelative(context.getPixels(R.dimen.item_header_margin), paddingTop, paddingEnd, paddingBottom)
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
