package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.github.kr328.clash.design.adapter.AboutItemAdapter
import com.github.kr328.clash.design.databinding.DesignAboutPageBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.patchDataSet
import com.github.kr328.clash.design.util.root

class AboutDesign(context: Context) : Design<AboutDesign.Request>(context) {
    enum class Request {
        OpenHelp,
        OpenSource,
    }

    private val binding = DesignAboutPageBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = AboutItemAdapter(context) {
        when (it.id) {
            "help" -> request(Request.OpenHelp)
            "source" -> request(Request.OpenSource)
        }
    }

    override val root: View
        get() = binding.root

    suspend fun patchItems(items: List<AboutItemAdapter.AboutItem>) {
        adapter.patchDataSet(adapter::items, items, false, AboutItemAdapter.AboutItem::id)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)
        binding.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.activityBarLayout.findViewById<ImageView>(R.id.activity_bar_close_view)?.visibility = View.GONE
        binding.activityBarLayout.findViewById<TextView>(R.id.activity_bar_title_view)?.apply {
            setText(R.string.about)
            setPaddingRelative(context.getPixels(R.dimen.item_header_margin), paddingTop, paddingEnd, paddingBottom)
        }
        binding.recyclerList.applyLinearAdapter(context, adapter)
    }
}
