package com.example.myapplication.base

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding

abstract class BasePopupWindow<VB : ViewBinding>(
    context: Context,
    val inflater: (LayoutInflater) -> VB,
    var height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
    var width:Int = WindowManager.LayoutParams.WRAP_CONTENT
): PopupWindow(context){

    protected val binding:VB by lazy { inflater(LayoutInflater.from(context)) }

    init {
        contentView = binding.root
        isFocusable = true
        isOutsideTouchable = true
        this.height = height
        this.width = width
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        initView()
        bindView()
    }
    open fun initView(){}
    open fun bindView(){}
}