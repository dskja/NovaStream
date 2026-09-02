package com.novastream.app.ui.profile

import androidx.lifecycle.ViewModel
import com.novastream.app.profile.ProfileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProfilePickerViewModel @Inject constructor(
    val profileManager: ProfileManager
) : ViewModel()
