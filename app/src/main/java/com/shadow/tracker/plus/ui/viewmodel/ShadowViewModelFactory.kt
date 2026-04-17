package com.shadow.tracker.plus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shadow.tracker.plus.data.local.TokenDao

class ShadowViewModelFactory(private val tokenDao: TokenDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShadowViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShadowViewModel(tokenDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
